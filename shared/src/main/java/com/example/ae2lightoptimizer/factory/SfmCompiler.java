package com.example.ae2lightoptimizer.factory;

import java.util.*;
import static com.example.ae2lightoptimizer.factory.FactoryProgram.*;

/** Lowers SFM triggers to the same durable machine; unsupported clauses fail before world access. */
public final class SfmCompiler {
    private final List<Instruction> code=new ArrayList<>();
    private final Set<String> tags=new LinkedHashSet<>();
    private final boolean recipe;
    private final int materials;
    private final int outputs;
    private int conditionDepth;
    private SfmCompiler(boolean recipe,int materials,int outputs){this.recipe=recipe;this.materials=materials;this.outputs=outputs;}
    public static FactoryProgram compile(String source,boolean recipe,int materials){return compile(source,recipe,materials,recipe?Integer.MAX_VALUE:0);}
    public static FactoryProgram compile(String source,boolean recipe,int materials,int outputs) {
        var parsed=SfmSyntax.parse(source);var c=new SfmCompiler(recipe,materials,outputs);
        c.emit(Op.COMPLETE,"",0,0,1);
        for(var trigger:parsed.triggers()) {
            int test=c.emit(Op.TIMER,(trigger.redstone()?"pulse":trigger.global()?"global":"local")+" "+trigger.offset(),0,trigger.ticks(),trigger.line());
            c.body(trigger.body());c.emit(Op.FORGET,"",0,0,trigger.line());c.patch(test,c.code.size());
        }
        c.emit(Op.WAIT,"",0,1,1);c.emit(Op.JUMP,"",0,0,1);c.emit(Op.DONE,"",0,0,1);
        for(var instruction:c.code)if(instruction.op()==Op.TEST)FactoryExpression.validate(instruction.argument(),c.tags,instruction.line(),recipe,materials,outputs);
        return new FactoryProgram(source,parsed.name(),c.tags,c.code,Map.of(),recipe);
    }
    private void body(List<SfmSyntax.Statement> statements) {
        for(var statement:statements) {
            if(statement instanceof SfmSyntax.Route route)route(route);
            else if(statement instanceof SfmSyntax.Forget forget) {
                if(forget.labels().isEmpty())emit(Op.FORGET,"",0,0,forget.line());
                else for(var label:labels(forget.labels(),forget.line()))emit(Op.FORGET,label,0,0,forget.line());
            } else if(statement instanceof SfmSyntax.Conditional condition) {
                var exits=new ArrayList<Integer>();
                for(var branch:condition.branches()) {
                    String expression=condition(branch.condition(),condition.line());
                    int test=emit(Op.TEST,expression,0,0,condition.line());body(branch.body());
                    exits.add(emit(Op.JUMP,"",0,0,condition.line()));patch(test,code.size());
                }
                body(condition.otherwise());for(int exit:exits)patch(exit,code.size());
            }
        }
    }
    private String condition(List<SfmSyntax.Token> tokens,int line) {
        if(conditionDepth>=64)throw error(line,"SFM condition nests more than 64 levels");
        conditionDepth++;
        try{return conditionBody(tokens,line);}finally{conditionDepth--;}
    }
    private String conditionBody(List<SfmSyntax.Token> tokens,int line) {
        if(tokens.isEmpty())throw error(line,"Missing SFM condition");
        int depth=0;
        for(String operator:List.of("or","and")) {
            depth=0;
            for(int i=0;i<tokens.size();i++) {
                if(tokens.get(i).is("("))depth++;
                if(tokens.get(i).is(")"))depth--;
                if(depth==0&&tokens.get(i).is(operator))return "("+condition(tokens.subList(0,i),line)+") "+operator+" ("+condition(tokens.subList(i+1,tokens.size()),line)+")";
            }
        }
        if(tokens.getFirst().is("not"))return "not ("+condition(tokens.subList(1,tokens.size()),line)+")";
        if(tokens.getFirst().is("(")&&tokens.getLast().is(")"))return "("+condition(tokens.subList(1,tokens.size()-1),line)+")";
        if(tokens.size()==1 && (tokens.getFirst().is("true")||tokens.getFirst().is("false")))return tokens.getFirst().text().toLowerCase(Locale.ROOT);
        int begin=tokens.getFirst().is("overall")?1:0;
        if(tokens.size()<begin+4 || !tokens.get(begin+1).is("has"))throw error(line,"Unsupported SFM condition; expected label HAS comparison quantity resource");
        String label=tag(tokens.get(begin),line);
        String comparison=switch(tokens.get(begin+2).text().toLowerCase(Locale.ROOT)) {
            case "gt",">"->">";case "lt","<"->"<";case "ge",">="->">=";case "le","<="->"<=";
            case "eq","="->"=";default->throw error(line,"Unsupported SFM comparison");
        };
        long amount=number(tokens.get(begin+3),line);
        String resource=resource(tokens.subList(begin+4,tokens.size()),line);
        return label+" has "+resource+" "+comparison+" "+amount;
    }
    private void route(SfmSyntax.Route route) {
        var access=new ArrayList<>(route.destinations());String face="";
        if(access.size()>=2 && access.getLast().is("side")) {
            face=switch(access.get(access.size()-2).text().toLowerCase(Locale.ROOT)) {
                case "top"->"up";case "bottom"->"down";case "north"->"north";case "south"->"south";
                case "east"->"east";case "west"->"west";case "null"->"";
                default->throw error(route.line(),"Unsupported SFM side qualifier");
            };
            access.subList(access.size()-2,access.size()).clear();
        }
        var destinations=labels(access,route.line());
        // Comma resource lists produce independent budgets, as in SFM.
        var resources=route.resources();String exclusions="";
        for(int i=0;i<resources.size();i++)if(resources.get(i).is("except")) {
            exclusions=excluded(resources.subList(i+1,resources.size()),route.line());resources=resources.subList(0,i);break;
        }
        int start=0,depth=0;
        for(int i=0;i<=resources.size();i++) {
            if(i<resources.size()) {if(resources.get(i).is("("))depth++;if(resources.get(i).is(")"))depth--;}
            if(i==resources.size() || (depth==0 && resources.get(i).is(","))) {
                var clause=resources.subList(start,i);long amount=Long.MAX_VALUE;
                boolean must=!clause.isEmpty()&&clause.getFirst().is("must");
                if(must){clause=clause.subList(1,clause.size());if(clause.isEmpty()||!clause.getFirst().text().matches("[0-9]+"))throw error(route.line(),"must requires a positive quantity");}
                if(!clause.isEmpty() && clause.getFirst().text().matches("[0-9]+")) {
                    amount=number(clause.getFirst(),route.line());clause=clause.subList(1,clause.size());
                    if(amount==0)throw error(route.line(),"SFM quantity must be positive");
                }
                String selector=resource(clause,route.line());
                if(!exclusions.isEmpty())selector=FactorySelector.parse(selector+"!("+exclusions+")").canonical();
                for(String destination:destinations)code.add(new Instruction(route.input()?Op.GET:Op.PUT,selector+"\u001f"+destination+"\u001f"+face,0,amount,route.line(),must));
                start=i+1;
            }
        }
    }
    private String excluded(List<SfmSyntax.Token> tokens,int line) {
        if(tokens.isEmpty())throw error(line,"Missing SFM exclusion");
        var values=new ArrayList<String>();int start=0;
        for(int i=0;i<=tokens.size();i++)if(i==tokens.size()||tokens.get(i).is(",")) {
            if(start<i)values.add(resource(tokens.subList(start,i),line));
            else if(i<tokens.size())throw error(line,"Missing SFM excluded resource");
            start=i+1;
        }
        return String.join(",",values);
    }
    private String resource(List<SfmSyntax.Token> tokens,int line) {
        if(tokens.isEmpty())return "minecraft::item";
        var value=new StringBuilder();
        for(var token:tokens) {
            if(Set.of("retain","each","with","without","except","or").contains(token.text().toLowerCase(Locale.ROOT)) && !token.quoted())
                throw error(line,"Unsupported SFM resource clause: "+token.text());
            value.append(token.text());
        }
        String id=value.toString();
        if(id.equals("*"))id="minecraft::item";
        else if(id.startsWith("item:"))id="minecraft::item/"+id.substring(5);
        else if(id.startsWith("fluid:"))id="minecraft::fluid/"+id.substring(6);
        else if(id.equals("fe::")||id.equals("forge_energy::"))id="neoforge::fe";
        else if(id.endsWith("::"))throw error(line,"Unsupported SFM resource type: "+id);
        else if(!id.contains(":")&&!id.startsWith("P")&&!id.startsWith("O"))id="minecraft:"+id;
        try {var selector=FactorySelector.parse(id);selector.validateParameters(recipe,materials,outputs);return selector.canonical();}
        catch(IllegalArgumentException failure){throw error(line,failure.getMessage());}
    }
    private List<String> labels(List<SfmSyntax.Token> tokens,int line) {
        if(tokens.isEmpty())throw error(line,"Missing SFM label");
        var result=new ArrayList<String>();boolean expect=true;
        for(var token:tokens) {
            if(token.is(",")){if(expect)throw error(line,"Missing SFM label");expect=true;}
            else {if(!expect)throw error(line,"Unsupported SFM access clause: "+token.text());result.add(tag(token,line));expect=false;}
        }
        return result;
    }
    private String tag(SfmSyntax.Token token,int line) {
        String value=token.text();
        if(!value.equals("source")&&!value.equals("storage")) {
            if(!FactoryTags.validName(value))throw error(line,"Invalid or reserved SFM label: "+value);
            tags.add(value);
        }
        return value;
    }
    private static long number(SfmSyntax.Token token,int line) {
        try {long value=Long.parseLong(token.text());if(value>=0)return value;}catch(NumberFormatException ignored){}
        throw error(line,"Expected nonnegative 64-bit quantity");
    }
    private int emit(Op op,String arg,int target,long amount,int line){code.add(new Instruction(op,arg,target,amount,line));return code.size()-1;}
    private void patch(int i,int target){var old=code.get(i);code.set(i,new Instruction(old.op(),old.argument(),target,old.amount(),old.line()));}
    private static CompileException error(int line,String message){return new CompileException(line,message);}
    private SfmCompiler(){throw new AssertionError();}
}
