package yaoshu.token.relay.channel.gemini;

import java.util.List;

public final class GeminiConstant {
    private GeminiConstant() {}
    public static final String CHANNEL_NAME = "google gemini";
    public static final List<String> MODEL_LIST = List.of(
            "gemini-2.5-flash", "gemini-2.5-pro", "gemini-2.0-flash",
            "gemini-2.0-flash-001", "gemini-2.0-flash-lite-001", "gemini-2.0-flash-lite",
            "gemini-2.5-flash-lite", "gemini-flash-latest", "gemini-flash-lite-latest",
            "gemini-pro-latest", "gemini-2.5-flash-native-audio-latest",
            "gemini-2.5-flash-preview-tts", "gemini-2.5-pro-preview-tts",
            "gemini-2.5-flash-image", "gemini-2.5-flash-lite-preview-09-2025",
            "gemini-3-pro-preview", "gemini-3-flash-preview", "gemini-3.1-pro-preview",
            "gemini-3.1-pro-preview-customtools", "gemini-3.1-flash-lite-preview",
            "gemini-3-pro-image-preview", "nano-banana-pro-preview",
            "gemini-3.1-flash-image-preview", "gemini-robotics-er-1.5-preview",
            "gemini-2.5-computer-use-preview-10-2025", "deep-research-pro-preview-12-2025",
            "gemma-3-1b-it", "gemma-3-4b-it", "gemma-3-12b-it",
            "gemma-3-27b-it", "gemma-3n-e4b-it", "gemma-3n-e2b-it",
            "gemini-embedding-001", "gemini-embedding-2-preview",
            "imagen-4.0-generate-001", "imagen-4.0-ultra-generate-001", "imagen-4.0-fast-generate-001",
            "veo-2.0-generate-001", "veo-3.0-generate-001", "veo-3.0-fast-generate-001",
            "veo-3.1-generate-preview", "veo-3.1-fast-generate-preview", "aqa"
    );
    public static final List<String> SAFETY_SETTING_LIST = List.of(
            "HARM_CATEGORY_HARASSMENT", "HARM_CATEGORY_HATE_SPEECH",
            "HARM_CATEGORY_SEXUALLY_EXPLICIT", "HARM_CATEGORY_DANGEROUS_CONTENT"
    );
}
