# Misha AI — Android assistant

Native Kotlin + Jetpack Compose Android starter for Misha AI.

## Included
- Premium dark purple/blue chat UI
- Voice input using Android speech recognition
- Android Text-to-Speech for assistant messages
- Basic app launching for selected common apps
- Configurable HTTPS AI backend endpoint
- Network timeout and visible error handling
- GitHub Actions workflow that builds and uploads a debug APK

## Build APK
1. Open the **Actions** tab.
2. Run **Build Misha AI APK** (or push a change to main).
3. Open the successful workflow run and download the `MishaAI-debug-apk` artifact.
4. Extract the artifact ZIP and install `app-debug.apk`.

## AI backend
The app expects a server endpoint accepting `POST {"message":"Hello"}` and returning `{"reply":"Hello"}`. Keep the Gemini API key on the server, never in Android source or APK.

## Limitations
This is a development starter, not a completed production assistant. Always-on wake-word, call screening, WhatsApp sending, lock-screen controls and physical-device testing are not yet implemented. Voice input depends on compatible speech services. Configure and test the AI backend before expecting Gemini answers.
