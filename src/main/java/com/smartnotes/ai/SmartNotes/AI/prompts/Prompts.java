package com.smartnotes.ai.SmartNotes.AI.prompts;

public class Prompts {

    private static final String NOTES_GENERATION_TEMPLATE = """
            You are an expert note-taker and educational content summarizer.
            
            Generate comprehensive, well-structured notes from the following transcript.
            
            **Guidelines:**
            - Create clear section headings
            - Highlight key points and main ideas
            - Include important details and examples
            - Use bullet points for clarity
            - Organize information logically
            - Make it easy to understand and study from
            
            **Language:** %s
            
            **Transcript:**
            %s
            
            **Generate detailed notes now:**
            """;

    public static String getNotesGenerationPrompt(String transcript, String language) {
        String lang = (language == null || language.isBlank()) ? "English" : language;
        return String.format(NOTES_GENERATION_TEMPLATE, lang, transcript);
    }
}