# Headless Compose

An Android library and demo app for capturing Jetpack Compose UI content to images without displaying it on screen.

## How It Works

The project uses a virtual display to render Compose content in a hidden `Presentation` window. The content is then captured using Compose's graphics layer API and converted to a bitmap image.

## Usage

```kotlin
val bitmap: Bitmap = headlessCapture(
    context = context,
    size = DpSize(600.dp, 800.dp),
    content = { YourComposableContent() }
).asAndroidBitmap()
```

## Credits

This project includes code adapted from [CaptureComposable.kt](https://gist.github.com/iamcalledrob/871568679ad58e64959b097d4ef30738) by [@iamcalledrob](https://github.com/iamcalledrob).

## License

[Apache License, Version 2.0](LICENSE).
