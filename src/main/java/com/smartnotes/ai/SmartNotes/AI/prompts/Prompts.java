package com.smartnotes.ai.SmartNotes.AI.prompts;

public class Prompts {

    // ============================================================
    // STEP 1: TOPIC EXTRACTION PROMPT
    // ============================================================
    private static final String TOPIC_EXTRACTION_TEMPLATE = """
            You are analyzing a video transcript to extract topics and subtopics.
            
            RULES:
            1. Return ONLY a JSON array, nothing else
            2. No markdown, no explanations, no extra text
            3. Format: [{"mainTopic": "Topic Name", "subtopics": ["Sub1", "Sub2"]}]
            
            LANGUAGE: %s
            
            TRANSCRIPT:
            %s
            
            JSON OUTPUT:
            """;

    // ============================================================
    // STEP 2: CONTENT GENERATION PROMPT
    // ============================================================
    private static final String CONTENT_GENERATION_TEMPLATE = """
            You are creating detailed educational notes for a specific topic from a video transcript.
            
            TOPIC: %s
            SUBTOPICS: %s
            
            RULES:
            1. Return ONLY a JSON object, nothing else
            2. No markdown code fences, no explanations
            3. Use this EXACT structure:
            
            {
              "title": "Topic Name",
              "subtopics": [
                {
                  "title": "Subtopic Name",
                  "description": "Brief overview (1-2 sentences)",
                  "content": "Detailed explanation with examples. Use [IMAGE: description] where images help. Use [TABLE: title] where tables help.",
                  "imagePositions": [
                    {
                      "position": 1,
                      "description": "What this image should show"
                    }
                  ],
                  "tablePositions": [
                    {
                      "position": 1,
                      "title": "Table Title",
                      "headers": ["Column1", "Column2"],
                      "rows": [
                        ["Value1", "Value2"],
                        ["Value3", "Value4"]
                      ]
                    }
                  ]
                }
              ]
            }
            
            4. Create content for ALL subtopics listed above
            5. Make content detailed and educational
            6. Write in %s language
            
            TRANSCRIPT:
            %s
            
            JSON OUTPUT:
            """;

    // ============================================================
    // FALLBACK: SIMPLE NOTES GENERATION
    // ============================================================
    private static final String SIMPLE_NOTES_GENERATION_TEMPLATE = """
            Create comprehensive educational notes from this video transcript.
            
            FORMAT:
            - Use ## for main headings
            - Use bullet points for key information
            - Include examples where relevant
            - Make it easy to study from
            
            LANGUAGE: %s
            
            TRANSCRIPT:
            %s
            
            NOTES:
            """;

    public static String getTopicExtractionPrompt(String transcript, String language) {
        String lang = (language == null || language.isBlank()) ? "English" : language;
        return String.format(TOPIC_EXTRACTION_TEMPLATE, lang, transcript);
    }

    public static String getContentGenerationPrompt(
            String mainTopic,
            String subtopics,
            String transcript,
            String language) {
        String lang = (language == null || language.isBlank()) ? "English" : language;
        return String.format(CONTENT_GENERATION_TEMPLATE, mainTopic, subtopics, lang, transcript);
    }

    public static String getSimpleNotesGenerationPrompt(String transcript, String language) {
        String lang = (language == null || language.isBlank()) ? "English" : language;
        return String.format(SIMPLE_NOTES_GENERATION_TEMPLATE, lang, transcript);
    }
}