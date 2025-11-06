# SpinoCare

SpinoCare is an AI-powered Android application that uses TensorFlow Lite to analyze MRI images for Modic changes in the spine and help clinicians identify spinal imaging findings.

## Features

- **T1/T2 Image Analysis**: Select T1 and T2 MRI images for comprehensive analysis
- **AI Analysis**: Uses a TensorFlow Lite model to detect Modic changes
- **Confidence Scores**: Shows detailed analysis results with confidence percentages
- **Modern UI**: Built with Jetpack Compose featuring SpinoCare branding
- **Smart Combination**: Analyzes both images and provides combined results

## How to Use

1. **Launch the app** and grant necessary permissions when prompted
2. **Select MRI Images**: Tap "T1 Image" and "T2 Image" to choose MRI scans from your gallery
3. **Automatic Analysis**: Once both images are selected, the app automatically analyzes them
4. **View Results**: The app shows:
   - Whether Modic changes are detected (from either image)
   - Combined confidence percentage
   - Detailed scores for both "No Modic" and "Modic Change" classifications

## Technical Details

- **Model**: TensorFlow Lite model (e.g. `modic_model.tflite`)
- **Input Size**: 224x224 pixels (configurable in `ModicModelHandler.kt`)
- **Analysis Method**: T1/T2 image analysis with smart result combination
- **Framework**: Jetpack Compose + Kotlin
- **Design**: SpinoCare branding with coral/red accent colors
- **Minimum SDK**: Android API 24 (Android 7.0)

## Project Structure

```
app/
├── src/main/
│   ├── assets/                         # Empty - models now downloaded on-demand
│   ├── java/com/example/modicanalyzer/
│   │   ├── SimpleMainActivity.kt       # Main UI with hybrid online/offline system
│   │   ├── ModicAnalyzer.kt           # Unified analyzer (online/offline switching)
│   │   ├── RemoteModelAnalyzer.kt     # Remote server inference
│   │   ├── LocalModelAnalyzer.kt      # Local TFLite inference
│   │   ├── SettingsActivity.kt        # User settings for inference mode
│   │   └── ImageUtils.kt              # Image processing utilities
│   └── AndroidManifest.xml            # App permissions and configuration
```

## Model Requirements

The app now uses a hybrid online/offline architecture:
- **Online Mode**: Remote inference via FastAPI server (default)
- **Offline Mode**: Local TensorFlow Lite model (user-downloadable, 49MB)
- **Model Format**: 224x224x3 RGB dual-input (T1 + T2 weighted MRI)
- **Output**: 2-class probability array [No Modic, Modic Change]

## Building and Running

1. Open the project in Android Studio
2. **No model setup required** - models are downloaded on-demand
3. Build and run the project on a device or emulator
4. Use Settings to switch between online/offline modes
5. Grant storage and internet permissions when prompted
6. Select T1 and T2 weighted MRI images to analyze

## Permissions

The app requires the following permissions:
- `READ_EXTERNAL_STORAGE` (Android < 13)
- `READ_MEDIA_IMAGES` (Android 13+)

These are automatically requested when needed.