package com.smartnotes.ai.SmartNotes.AI.prompts;

public class Prompts {

    // ============================================================
    // STEP 1: TOPIC EXTRACTION PROMPT
    // ============================================================
    private static final String TOPIC_EXTRACTION_TEMPLATE = """
            **CONTEXT:**
            You are an AI assistant helping to create structured educational notes from a video transcript.
            This is STEP 1 of a multi-step process where we first need to identify what topics are covered in the video.
            
            **YOUR TASK:**
            Analyze the transcript below and extract ALL main topics and their subtopics.
            - Main topics are broad categories (e.g., "Spring Framework", "Database Design", "Multithreading")
            - Subtopics are specific concepts under each main topic (e.g., under "Spring Framework": "Dependency Injection", "Bean Lifecycle", "Auto Configuration")
            
            **IMPORTANT RULES:**
            1. You MUST return ONLY valid JSON - no explanations, no markdown, no extra text
            2. Use this EXACT format:
            [
              {
                "mainTopic": "Main Topic Name",
                "subtopics": ["Subtopic 1", "Subtopic 2", "Subtopic 3"]
              }
            ]
            3. Be comprehensive - capture ALL topics discussed
            4. Keep topic names concise but clear
            5. If only one main topic exists, still return it as an array with one element
            
            **EXAMPLE OUTPUT:**
            [
              {
                "mainTopic": "Spring Framework Basics",
                "subtopics": ["Dependency Injection", "Inversion of Control", "Bean Lifecycle", "ApplicationContext"]
              },
              {
                "mainTopic": "Spring Boot Features",
                "subtopics": ["Auto Configuration", "Starter Dependencies", "Embedded Servers", "Actuator"]
              }
            ]
            
            **LANGUAGE:** %s
            
            **TRANSCRIPT TO ANALYZE:**
            %s
            
            **NOW RETURN ONLY THE JSON ARRAY (no other text):**
            """;

    // ============================================================
    // STEP 2: CONTENT GENERATION PROMPT
    // ============================================================
    private static final String CONTENT_GENERATION_TEMPLATE = """
            **CONTEXT:**
            You are an AI assistant creating detailed educational notes from a video transcript.
            This is STEP 2 of our process. In STEP 1, we identified topics. Now we need detailed content for ONE specific topic.
            
            **WHAT HAPPENED SO FAR:**
            - We analyzed a video transcript
            - We identified this main topic: "%s"
            - Under this topic, we found these subtopics: %s
            
            **YOUR TASK:**
            Create comprehensive, detailed educational content for ONLY this topic and its subtopics based on the transcript.
            
            **IMPORTANT RULES:**
            1. You MUST return ONLY valid JSON - no explanations, no markdown, no extra text
            2. For each subtopic, provide:
               - A brief description (1-2 sentences overview)
               - Detailed content (as much as needed - NO length limit)
               - Where diagrams/images would help understanding
               - Where tables would organize information better
            
            3. Use this EXACT JSON format:
            {
              "title": "Main Topic Name",
              "subtopics": [
                {
                  "title": "Subtopic Name",
                  "description": "Brief 1-2 sentence overview",
                  "content": "Detailed explanation with examples. When you think an image would help, write [IMAGE: description of what image should show]. When a table would help, write [TABLE: table_title | header1,header2,header3 | row1col1,row1col2,row1col3 | row2col1,row2col2,row2col3]. Continue with more content.",
                  "imagePositions": [
                    {
                      "position": 1,
                      "description": "Detailed description of what this image should illustrate"
                    }
                  ],
                  "tablePositions": [
                    {
                      "position": 1,
                      "title": "Table Title",
                      "headers": ["Header 1", "Header 2", "Header 3"],
                      "rows": [
                        ["Row 1 Col 1", "Row 1 Col 2", "Row 1 Col 3"],
                        ["Row 2 Col 1", "Row 2 Col 2", "Row 2 Col 3"]
                      ]
                    }
                  ]
                }
              ]
            }
            
            **EXAMPLE OUTPUT:**
            {
              "title": "Spring Dependency Injection",
              "subtopics": [
                {
                  "title": "Constructor Injection",
                  "description": "Constructor injection is the most recommended way to inject dependencies in Spring, providing immutability and mandatory dependencies.",
                  "content": "Constructor injection involves passing dependencies through a class constructor. This approach has several advantages: it makes dependencies explicit, ensures immutability, and makes testing easier. [IMAGE: Diagram showing a class with constructor parameters being injected by Spring container] Here's how it works: When Spring creates a bean, it looks at the constructor parameters and automatically injects the required beans. [TABLE: Injection Types Comparison | Type,Immutability,Optional Dependencies,Testing | Constructor,Yes,No,Easy | Setter,No,Yes,Moderate | Field,No,No,Difficult] The @Autowired annotation can be used on constructors, though it's optional if there's only one constructor.",
                  "imagePositions": [
                    {
                      "position": 1,
                      "description": "UML-style diagram showing Spring container injecting dependencies via constructor - arrows from container to constructor parameters"
                    }
                  ],
                  "tablePositions": [
                    {
                      "position": 1,
                      "title": "Injection Types Comparison",
                      "headers": ["Type", "Immutability", "Optional Dependencies", "Testing"],
                      "rows": [
                        ["Constructor", "Yes", "No", "Easy"],
                        ["Setter", "No", "Yes", "Moderate"],
                        ["Field", "No", "No", "Difficult"]
                      ]
                    }
                  ]
                }
              ]
            }
            
            **GUIDELINES FOR CONTENT:**
            - Make explanations clear and easy to understand
            - Include practical examples from the transcript
            - Suggest images for: processes, flows, architectures, diagrams, comparisons
            - Suggest tables for: comparisons, lists of options, configuration values, pros/cons
            - Write in %s language
            - Be thorough - there is NO length limit
            
            **FULL TRANSCRIPT FOR CONTEXT:**
            %s
            
            **NOW RETURN ONLY THE JSON OBJECT (no other text before or after):**
            """;

    // ============================================================
    // FALLBACK: SIMPLE NOTES GENERATION
    // ============================================================
    private static final String SIMPLE_NOTES_GENERATION_TEMPLATE = """
            **CONTEXT:**
            You are creating educational notes from a video transcript.
            Due to an error in structured processing, we need a simple text-based summary.
            
            **YOUR TASK:**
            Generate comprehensive, well-structured notes as plain text (not JSON).
            
            **GUIDELINES:**
            - Create clear section headings using ## for main topics
            - Use bullet points for key information
            - Include code examples if relevant
            - Highlight important concepts
            - Make it easy to study from
            - Write in %s language
            
            **TRANSCRIPT:**
            %s
            
            **GENERATE NOTES NOW:**
            """;

    // ============================================================
    // PUBLIC METHODS
    // ============================================================

    public static String getTopicExtractionPrompt(String transcript, String language) {
        String lang = (language == null || language.isBlank()) ? "English" : language;
        return String.format(TOPIC_EXTRACTION_TEMPLATE, lang, transcript);
    }

    public static String getContentGenerationPrompt(String mainTopic, String subtopics, String transcript, String language) {
        String lang = (language == null || language.isBlank()) ? "English" : language;
        return String.format(CONTENT_GENERATION_TEMPLATE, mainTopic, subtopics, lang, transcript);
    }

    public static String getSimpleNotesGenerationPrompt(String transcript, String language) {
        String lang = (language == null || language.isBlank()) ? "English" : language;
        return String.format(SIMPLE_NOTES_GENERATION_TEMPLATE, lang, transcript);
    }
}