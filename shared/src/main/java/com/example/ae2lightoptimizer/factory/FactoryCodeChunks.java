package com.example.ae2lightoptimizer.factory;

import java.util.ArrayList;
import java.util.List;

/** Preserve UTF-16 surrogate pairs across separately UTF-8 encoded transport or NBT chunks. */
public final class FactoryCodeChunks {
    public static List<String> split(String text,int limit) {
        if(limit<2)throw new IllegalArgumentException("Chunk size must be at least two");
        var result=new ArrayList<String>();
        for(int start=0;start<text.length();) {
            int end=Math.min(text.length(),start+limit);
            if(end<text.length() && Character.isHighSurrogate(text.charAt(end-1)) && Character.isLowSurrogate(text.charAt(end)))end--;
            result.add(text.substring(start,end));start=end;
        }
        if(result.isEmpty())result.add("");
        return List.copyOf(result);
    }
    private FactoryCodeChunks() {}
}
