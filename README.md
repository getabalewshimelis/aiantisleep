# AI Anti-Sleep Detector


AI Anti-Sleep Detector is an advanced Android application designed to prevent drowsiness by monitoring user alertness through multiple methods, including real-time computer vision analysis, external hardware integration, and AI-powered sleep cycle management.

## Features

-   **Real-time Drowsiness Detection:** Utilizes the device's front camera and Google's MediaPipe Face Mesh to calculate the Eye Aspect Ratio (EAR) in real-time. If the user's eyes remain closed beyond a set threshold, a multi-modal alarm is triggered.
-   **Multi-Modal Alerts:** When drowsiness is detected, the app initiates a loud audible alarm, device vibration, and a flashing on-screen warning to alert the user immediately.
-   **Arduino Integration:** Supports connection to an Arduino device via USB for receiving sleep signals from external hardware sensors, offering a robust alternative or supplementary detection method.
-   **Sleep Data Visualization:** Logs all detected sleep events from both the camera and Arduino. It presents a historical bar chart of sleep event frequency within an 8-hour window, providing insights into patterns of drowsiness.
-   **AI-Powered Sleep Assistant:** Integrates Google's Gemini AI to provide a "Smart Antisleep AI" feature. This includes:
    -   **AI Sleep Insights:** On-demand tips for healthy sleep habits and staying alert.
    -   **Sleep Time Predictor:** Recommends optimal bedtimes based on the user's desired wake-up time to align with natural 90-minute sleep cycles.

## How It Works

The application is structured into three main components accessible via a bottom navigation bar.

### 1. Camera-Based Detection (`CameraFragment`)

-   **Image Processing:** The app uses `CameraX` to access the front camera feed. Each frame is passed to Google's MediaPipe Face Mesh solution.
-   **Eye Aspect Ratio (EAR):** From the 468 facial landmarks provided by MediaPipe, the application calculates the EAR. This ratio quantifies eye-opening based on the vertical and horizontal distances of key eye landmarks.
-   **Drowsiness Logic:** If the calculated EAR drops below a predefined threshold (`EAR_THRESHOLD = 0.21`) for a consecutive number of frames (`SLEEP_FRAME_THRESHOLD = 15`), the system classifies the user as sleeping and triggers the alert.
-   **Visual Feedback:** A custom `FaceMeshOverlayView` draws the contours of the user's eyes directly on the camera preview, changing color from green (awake) to red (sleeping) for intuitive feedback.

### 2. Arduino & Data Graph (`ArduinoFragment`)

-   **USB Serial Connection:** Using the `usb-serial-for-android` library, this fragment establishes a serial connection with a connected Arduino device.
-   **External Events:** It listens for incoming data and logs a sleep event whenever a "sleep" message is received from the Arduino.
-   **Data Visualization:** All sleep events, whether from the camera or Arduino, are stored persistently using `SharedPreferences`. This data is then rendered in a bar chart using `MPAndroidChart`, showing the number of sleep events per hour over the last 8 hours.

### 3. AI Settings & Tools (`SettingsFragment`)

-   **Gemini Integration:** This fragment uses the Google Gemini AI (`gemini-1.5-flash` model) to provide intelligent features.
-   **AI Prompts:** The app sends structured prompts to the Gemini API to:
    1.  Generate concise tips for healthy sleep and driving alertness.
    2.  Calculate and suggest optimal sleep times by working backward in 90-minute cycles from a user-specified wake-up time.

## Technologies Used

-   **Platform:** Android (Java)
-   **Computer Vision:** Google MediaPipe (Face Mesh)
-   **Camera:** Android CameraX
-   **AI Model:** Google Gemini
-   **Charting:** MPAndroidChart
-   **Hardware Integration:** `usb-serial-for-android` for Arduino communication

## Setup and Installation

To build and run this project, follow these steps:

1.  **Clone the repository:**
    ```sh
    git clone https://github.com/getabalewshimelis/aiantisleep.git
    ```

2.  **Open in Android Studio:**
    -   Open Android Studio and select `Open an existing project`.
    -   Navigate to the cloned `aiantisleep` directory and open it.

3.  **Configure API Key:**
    -   This project requires a Google Gemini API key to power the AI features. You can obtain one from [Google AI Studio](https://aistudio.google.com/app/apikey).
    -   In the project, navigate to `app/src/main/java/com/gech/antisleepdetector/SettingsFragment.java`.
    -   Locate the `initSmartAI` method and replace the placeholder API key with your own:

      ```java
      // ...
      GenerativeModel gm = new GenerativeModel(
              "gemini-1.5-flash", 
              "YOUR_API_KEY_HERE" // Replace this string
      );
      // ...
      ```

4.  **Build and Run:**
    -   Connect an Android device or start an emulator.
    -   Build and run the application from Android Studio.
    -   Grant the required camera permissions when prompted.
