package com.example.ae2lightoptimizer.client;

import net.minecraft.network.chat.Component;

/** Localize on the receiving client, so each player uses their own language. */
public final class FactoryMessages {
    public static Component status(String message) {
        if(message.isEmpty())return Component.empty();
        switch(message) {
            case "Saved": return Component.translatable("gui.ae2lightoptimizer.factory.saved");
            case "Uploaded": return Component.translatable("gui.ae2lightoptimizer.factory.uploaded");
            case "Provider unavailable": return Component.translatable("gui.ae2lightoptimizer.factory.provider_unavailable");
            case "Encode a recipe first": return Component.translatable("gui.ae2lightoptimizer.factory.recipe_required");
            case "Invalid recipe": return Component.translatable("gui.ae2lightoptimizer.factory.invalid_recipe");
            case "Provider is full": return Component.translatable("gui.ae2lightoptimizer.factory.provider_full");
            case "Provider rejected pattern": return Component.translatable("gui.ae2lightoptimizer.factory.pattern_rejected");
            case "Insert a Loop Factory Pattern": return Component.translatable("gui.ae2lightoptimizer.factory.insert_pattern");
            case "This terminal accepts recipe-free patterns only": return Component.translatable("gui.ae2lightoptimizer.factory.recipe_free_only");
            case "Unbound": return Component.translatable("gui.ae2lightoptimizer.factory.unbound");
        }
        var line=java.util.regex.Pattern.compile("^Line ([0-9]+): (.*)$",java.util.regex.Pattern.DOTALL).matcher(message);
        if(line.matches())return Component.translatable("gui.ae2lightoptimizer.factory.error_line",line.group(1),reason(line.group(2)));
        return reason(message);
    }
    private static Component reason(String message) {
        if(message.equals("Waiting for transfer resources or destination capacity"))return Component.translatable("gui.ae2lightoptimizer.factory.waiting_transfer");
        if(message.equals("must requires a positive quantity"))return Component.translatable("gui.ae2lightoptimizer.factory.error.must_quantity");
        if(message.equals("Function recursion exceeds 64 frames"))return Component.translatable("gui.ae2lightoptimizer.factory.error.recursion");
        if(message.equals("Loop makes no progress; use wait or redstone for at least 1 tick"))return Component.translatable("gui.ae2lightoptimizer.factory.error.loop_stuck");
        if(message.equals("Recipe execution exceeded its finite instruction budget"))return Component.translatable("gui.ae2lightoptimizer.factory.error.recipe_budget");
        if(message.equals("Execution did not yield within 4096 instructions; insert wait 1 tick"))return Component.translatable("gui.ae2lightoptimizer.factory.error.tick_budget");
        if(message.equals("Return outside function"))return Component.translatable("gui.ae2lightoptimizer.factory.error.return_outside");
        if(message.equals("Factory name must contain 1 to 128 printable characters"))return Component.translatable("gui.ae2lightoptimizer.factory.error.name_length");
        var column=java.util.regex.Pattern.compile("^(.*) at selector column ([0-9]+)$").matcher(message);
        if(column.matches())return Component.translatable("gui.ae2lightoptimizer.factory.error.selector_column",column.group(2),reason(column.group(1)));
        var required=java.util.regex.Pattern.compile("^([PO][0-9]+) requires a recipe$").matcher(message);
        if(required.matches())return Component.translatable("gui.ae2lightoptimizer.factory.error.parameter_recipe",required.group(1));
        var outputRange=java.util.regex.Pattern.compile("^(O[0-9]+) exceeds the recipe's ([0-9]+) outputs$").matcher(message);
        if(outputRange.matches())return Component.translatable("gui.ae2lightoptimizer.factory.error.output_range",outputRange.group(1),outputRange.group(2));
        if(message.startsWith("Invalid recipe output O"))return Component.translatable("gui.ae2lightoptimizer.factory.error.output_invalid",message.substring(22));
        var range=java.util.regex.Pattern.compile("^(P[0-9]+) exceeds the recipe's ([0-9]+) materials$").matcher(message);
        if(range.matches())return Component.translatable("gui.ae2lightoptimizer.factory.error.parameter_range",range.group(1),range.group(2));
        if(message.equals("Code blocks nest more than 64 levels"))return Component.translatable("gui.ae2lightoptimizer.factory.error.code_depth");
        if(message.equals("Boolean expression nests more than 64 levels"))return Component.translatable("gui.ae2lightoptimizer.factory.error.boolean_depth");
        if(message.equals("SFM condition nests more than 64 levels"))return Component.translatable("gui.ae2lightoptimizer.factory.error.sfm_condition_depth");
        if(message.equals("SFM blocks nest more than 64 levels"))return Component.translatable("gui.ae2lightoptimizer.factory.error.sfm_block_depth");
        if(message.equals("Missing SFM condition"))return Component.translatable("gui.ae2lightoptimizer.factory.error.sfm_condition_missing");
        if(message.equals("Unsupported SFM condition; expected label HAS comparison quantity resource"))return Component.translatable("gui.ae2lightoptimizer.factory.error.sfm_condition");
        if(message.equals("Unsupported SFM comparison"))return Component.translatable("gui.ae2lightoptimizer.factory.error.sfm_comparison");
        if(message.equals("Unsupported SFM side qualifier"))return Component.translatable("gui.ae2lightoptimizer.factory.error.sfm_side");
        if(message.equals("SFM quantity must be positive"))return Component.translatable("gui.ae2lightoptimizer.factory.error.sfm_positive");
        if(message.equals("Missing SFM exclusion"))return Component.translatable("gui.ae2lightoptimizer.factory.error.sfm_exclusion");
        if(message.equals("Missing SFM excluded resource"))return Component.translatable("gui.ae2lightoptimizer.factory.error.sfm_excluded_resource");
        if(message.equals("Missing SFM label"))return Component.translatable("gui.ae2lightoptimizer.factory.error.sfm_label");
        if(message.equals("Expected nonnegative 64-bit quantity"))return Component.translatable("gui.ae2lightoptimizer.factory.error.sfm_quantity");
        if(message.equals("SFM NAME requires a quoted name"))return Component.translatable("gui.ae2lightoptimizer.factory.error.sfm_name");
        if(message.equals("SFM program requires an EVERY trigger"))return Component.translatable("gui.ae2lightoptimizer.factory.error.sfm_trigger");
        if(message.equals("SFM interval exceeds 64-bit ticks"))return Component.translatable("gui.ae2lightoptimizer.factory.error.sfm_interval_overflow");
        if(message.equals("Expected TICKS or SECONDS"))return Component.translatable("gui.ae2lightoptimizer.factory.error.sfm_unit");
        if(message.equals("Missing IF condition"))return Component.translatable("gui.ae2lightoptimizer.factory.error.if_missing");
        if(message.equals("Missing ELSE IF condition"))return Component.translatable("gui.ae2lightoptimizer.factory.error.else_if_missing");
        if(message.equals("Missing SFM destination label"))return Component.translatable("gui.ae2lightoptimizer.factory.error.sfm_destination");
        if(message.equals("Unexpected closing parenthesis"))return Component.translatable("gui.ae2lightoptimizer.factory.error.closing_parenthesis");
        if(message.equals("Unclosed SFM parentheses"))return Component.translatable("gui.ae2lightoptimizer.factory.error.sfm_parenthesis");
        if(message.equals("Unexpected end of SFM code"))return Component.translatable("gui.ae2lightoptimizer.factory.error.sfm_end");
        if(message.equals("Interval must be positive"))return Component.translatable("gui.ae2lightoptimizer.factory.error.interval_positive");
        if(message.equals("Expected nonnegative 64-bit integer"))return Component.translatable("gui.ae2lightoptimizer.factory.error.nonnegative");
        if(message.equals("SFM code exceeds 65536 characters"))return Component.translatable("gui.ae2lightoptimizer.factory.error.sfm_length");
        if(message.equals("Unclosed SFM string"))return Component.translatable("gui.ae2lightoptimizer.factory.error.sfm_string");
        if(message.equals("Unexpected SFM character"))return Component.translatable("gui.ae2lightoptimizer.factory.error.sfm_character");
        if(message.equals("Invalid comparison"))return Component.translatable("gui.ae2lightoptimizer.factory.error.comparison");
        if(message.equals("Recipe parameters start at P1 or O1"))return Component.translatable("gui.ae2lightoptimizer.factory.error.parameter_first");
        if(message.equals("Resource selector exceeds 4096 characters"))return Component.translatable("gui.ae2lightoptimizer.factory.error.selector_length");
        if(message.equals("Unexpected selector suffix"))return Component.translatable("gui.ae2lightoptimizer.factory.error.selector_suffix");
        if(message.equals("Selector exclusions nest more than 32 levels"))return Component.translatable("gui.ae2lightoptimizer.factory.error.selector_depth");
        if(message.equals("Expected resource or resource type"))return Component.translatable("gui.ae2lightoptimizer.factory.error.selector_resource");
        if(message.equals("Expected recipe parameter P1, P2, ... or O1, O2, ..."))return Component.translatable("gui.ae2lightoptimizer.factory.error.selector_parameter");
        if(message.equals("Invalid resource type separator"))return Component.translatable("gui.ae2lightoptimizer.factory.error.selector_separator");
        if(message.equals("Expected !(B,C)"))return Component.translatable("gui.ae2lightoptimizer.factory.error.selector_exclusion");
        if(message.equals("Missing closing exclusion parenthesis"))return Component.translatable("gui.ae2lightoptimizer.factory.error.selector_close");
        if(message.equals("Expected comma between exclusions"))return Component.translatable("gui.ae2lightoptimizer.factory.error.selector_comma");
        if(message.startsWith("Unsupported SFM resource clause: "))return Component.translatable("gui.ae2lightoptimizer.factory.error.sfm_resource_clause",message.substring(33));
        if(message.startsWith("Unsupported SFM resource type: "))return Component.translatable("gui.ae2lightoptimizer.factory.error.sfm_resource_type",message.substring(31));
        if(message.startsWith("Unsupported SFM access clause: "))return Component.translatable("gui.ae2lightoptimizer.factory.error.sfm_access_clause",message.substring(31));
        if(message.startsWith("Invalid or reserved SFM label: "))return Component.translatable("gui.ae2lightoptimizer.factory.error.sfm_reserved",message.substring(31));
        if(message.startsWith("Unknown SFM statement: "))return Component.translatable("gui.ae2lightoptimizer.factory.error.sfm_statement",message.substring(23));
        if(message.startsWith("Invalid recipe parameter: "))return Component.translatable("gui.ae2lightoptimizer.factory.error.parameter_invalid",message.substring(26));
        if(message.matches("Expected [A-Z_]+"))return Component.translatable("gui.ae2lightoptimizer.factory.error.expected_keyword",message.substring(9));
        if(message.startsWith("Unknown function: "))return Component.translatable("gui.ae2lightoptimizer.factory.error.unknown_function",message.substring(18));
        if(message.startsWith("Invalid or reserved name: "))return Component.translatable("gui.ae2lightoptimizer.factory.error.reserved_name",message.substring(26));
        if(message.startsWith("Unknown tag: "))return Component.translatable("gui.ae2lightoptimizer.factory.error.unknown_tag",message.substring(13));
        if(message.startsWith("Import tag before use: "))return Component.translatable("gui.ae2lightoptimizer.factory.error.import_tag",message.substring(23));
        if(message.startsWith("Unknown face: "))return Component.translatable("gui.ae2lightoptimizer.factory.error.unknown_face",message.substring(14));
        if(message.startsWith("Duplicate name: "))return Component.translatable("gui.ae2lightoptimizer.factory.error.duplicate_name",message.substring(16));
        if(message.startsWith("Invalid expression: "))return Component.translatable("gui.ae2lightoptimizer.factory.error.invalid_expression",message.substring(20));
        if(message.startsWith("Unmatched "))return Component.translatable("gui.ae2lightoptimizer.factory.error.unmatched",message.substring(10));
        if(message.equals("Code exceeds 65536 characters"))return Component.translatable("gui.ae2lightoptimizer.factory.error.too_long");
        if(message.equals("Use spaces, not tabs, for indentation"))return Component.translatable("gui.ae2lightoptimizer.factory.error.indent_spaces");
        if(message.equals("Unexpected indentation"))return Component.translatable("gui.ae2lightoptimizer.factory.error.indent_unexpected");
        if(message.equals("import must be at top level"))return Component.translatable("gui.ae2lightoptimizer.factory.error.import_top");
        if(message.equals("Expected top-level name \"Factory name\""))return Component.translatable("gui.ae2lightoptimizer.factory.error.name_top");
        if(message.equals("Expected do after condition"))return Component.translatable("gui.ae2lightoptimizer.factory.error.condition_do");
        if(message.equals("Missing condition"))return Component.translatable("gui.ae2lightoptimizer.factory.error.condition_missing");
        if(message.equals("Recipe patterns cannot contain unconditional loops"))return Component.translatable("gui.ae2lightoptimizer.factory.error.recipe_loop");
        if(message.equals("Functions must be declared at top level"))return Component.translatable("gui.ae2lightoptimizer.factory.error.function_top");
        if(message.equals("Function requires matching end"))return Component.translatable("gui.ae2lightoptimizer.factory.error.function_end");
        if(message.equals("Expected resource from tag [on face]"))return Component.translatable("gui.ae2lightoptimizer.factory.error.get_syntax");
        if(message.equals("Expected resource into tag [on face]"))return Component.translatable("gui.ae2lightoptimizer.factory.error.put_syntax");
        if(message.equals("Invalid face clause"))return Component.translatable("gui.ae2lightoptimizer.factory.error.face_clause");
        if(message.equals("Expected redstone tag number unit"))return Component.translatable("gui.ae2lightoptimizer.factory.error.redstone_syntax");
        if(message.equals("Expected wait number unit"))return Component.translatable("gui.ae2lightoptimizer.factory.error.wait_syntax");
        if(message.equals("Unknown time unit"))return Component.translatable("gui.ae2lightoptimizer.factory.error.time_unit");
        if(message.equals("Duration overflows 64-bit ticks"))return Component.translatable("gui.ae2lightoptimizer.factory.error.time_overflow");
        if(message.equals("Expected indented block"))return Component.translatable("gui.ae2lightoptimizer.factory.error.indent_missing");
        if(message.equals("Expected positive 64-bit integer"))return Component.translatable("gui.ae2lightoptimizer.factory.error.positive_integer");
        return Component.literal(message);
    }
    private FactoryMessages(){}
}
