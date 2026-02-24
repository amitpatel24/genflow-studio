package dev.chat.dto;

import java.util.List;

public record CodeResponse(
    boolean success,
    String explanation,
    List<CodeBlock> codeBlocks,
    String error
) {
    public record CodeBlock(
        String language,
        String code,
        String filename
    ) {}
    
    public static CodeResponse success(String explanation, List<CodeBlock> codeBlocks) {
        return new CodeResponse(true, explanation, codeBlocks, null);
    }
    
    public static CodeResponse error(String error) {
        return new CodeResponse(false, null, null, error);
    }
}
