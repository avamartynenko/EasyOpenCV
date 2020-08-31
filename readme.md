# EasyOpenCV

NOTE: SDK v5.1+ is required to use this

NOTE: an OpenRC-based SDK is NOT required to use this

Finally, a straightforward and easy way to use OpenCV on an FTC robot! With this library, you can **go from a stock SDK to running a sample OpenCV OpMode, with either an internal or external camera, in just a few minutes!**

Features at a glance:

 - **Supports concurrent streaming from:**
     - An internal camera and a webcam
     - Two webcams
     - Two internal cameras *(select devices; internal cameras must not share the same bus)*
 - Supports Driver Station camera preview feature introduced in SDK v5.1
 - Supports tapping on the viewport to cycle through the various stages of a pipeline (see [PipelineStageSwitchingExample](https://github.com/OpenFTC/EasyOpenCV/blob/master/examples/src/main/java/org/openftc/easyopencv/examples/PipelineStageSwitchingExample.java))
 - Supports using webcams directly with OpenCV instead of going through a Vuforia instance
 - Supports changing pipelines on-the-fly (while a streaming session is in flight)
 - Supports dynamically pausing/resuming live viewport to save battery and CPU time
 -  Support for rotating stream based on physical camera orientation (e.g. use a webcam in portrait without having to mess with rotation yourself)
 - Loads 10MB native library for OpenCV from internal storage to prevent bloating the APK
 
## Device compatibility:

Unfortunately, due to a [known bug with OpenCV 4.x](https://github.com/opencv/opencv/issues/15389), EasyOpenCV is only compatible with devices that run Android 5.0 or higher. For FTC, this means that it is incompatible with the ZTE Speed. EasyOpenCV will work fine on all other FTC-legal devices (including the new Control Hub).

## Documentation:

 - [Javadocs](https://javadoc.io/doc/org.openftc/easyopencv/1.4.1/index.html)
 - [Example programs](https://github.com/OpenFTC/EasyOpenCV/tree/master/examples/src/main/java/org/openftc/easyopencv/examples)

## Installation instructions:

**IMPORTANT NOTE: This tutorial assumes you are starting with a clean SDK project. This library includes the OpenCV Android SDK, so if you have already installed OpenCV in your project through the traditional means, you will need to remove it first. Otherwise, you will get a compiler error that multiple files define the same class.**

**IMPORTANT NOTE #2: Do NOT locally clone and/or import this project unless you want to develop this library itself! If you're just a normal user, follow the below instructions verbatim.**

1. Open your FTC SDK Android Studio project
2. Open the `build.common.gradle` file:

    ![img-here](doc/images/build-common-gradle.png)

3. Add `jcenter()` to the `repositories` block at the bottom:

    ![img-here](doc/images/jcenter.png)

4. Open the `build.gradle` file for the TeamCode module:

    ![img-here](doc/images/teamcode-gradle.png)

5. At the bottom, add this:

        dependencies {
            implementation 'org.openftc:easyopencv:1.4.1'
         }
         
6. Open the `build.common.gradle` file, and find the line `minSdkVersion 19`, and replace it with `minSdkVersion 23`

7. Now perform a Gradle Sync:

    ![img-here](doc/images/gradle-sync.png)

8. Because EasyOpenCv depends on [OpenCV-Repackaged](https://github.com/OpenFTC/OpenCV-Repackaged), you will also need to copy [`libOpenCvNative.so`](https://github.com/OpenFTC/OpenCV-Repackaged/blob/master/doc/libOpenCvNative.so) from the `/doc` folder of that repo into the `FIRST` folder on the USB storage of the Robot Controller (i.e. connect the Robot Controller to your computer with a USB cable, put it into MTP mode, and drag 'n drop the file) .

9. Congrats, you're ready to go! Now check out the [example OpModes](https://github.com/OpenFTC/EasyOpenCV/tree/master/examples/src/main/java/org/openftc/easyopencv/examples).


## Changelog:

### v1.4.1

 - Transitive dependency on OpenCV-Repackged updated to 4.1.0-C, which specifically handles error case of failure to load 32-bit library when FTC Robot Controller app has already loaded another native library as 64-bit
 - Fixes issue which prevented webcams from initializing in v1.4.0 which was found in prerelease testing, fixed, and yet somehow didn't make it into git...

### v1.4.0

 - Adds support for Android Camera2 API
     - New `OpenCvInternalCamera2` interface. Camera2 instances can be obtained from `OpenCvCameraFactory`, just like other types
     - Supports manual control over sensor parameters:
         - ISO (gain)
         - Exposure
         - Focus
         - White balance
         - Frame interval (FPS)
 - Make `OpenCvCamera` interface extend `CameraStreamSource` so that casting to implementation objects isn't required to use a camera as a stream source for something other than the DS
 - Adds `setViewportRenderingPolicy()` API to `OpenCvCamera interface`, provides option to:
     - `MAXIMIZE_EFFICIENCY` Keep viewport behavior as it always has been, OR
     - `OPTIMIZE_VIEW` At the expense of CPU time (and viewport smoothness), automatically orient preview image such that it's not constantly 90 degrees out from expected with an internal camera when the physical device orientation does not match the streaming orientation
 - Add memory leak detector for pipelines
     - Not 100% accurate but, seems to be fairly effective
     - Has a crude garbage collector run detector
     - Can be enabled/disabled or have parameters tweaked by modifying superclass variables from your pipeline constructor
 - Add `init(Mat m)` method to pipeline class, which will be called with the first frame from the camera, allowing you to initialize submats and the like for your pipeline
 - Adds pipeline utility function for saving Mats to disk.
     - Save function clones input mat and writes to disk asynchronously to prevent stalling pipeline
     - Up to 5 save operations can be running simultaneously; once this limit is reached, the pipeline will be stalled until one has completed
 - Adds APIs for closing and opening the camera asynchronously. This is now the recommended way to open and close, as it can help to prevent `stuckInXYZ()` issues and the like. Please consult the `OpenCvCamera` interface javadoc for details
 - Adds support for switchable webcams
     - New `OpenCvSwitchableWebcam` interface. Instances can be obtained from `OpenCvCameraFactory`, just like other types
 - Fix deadlock when closing webcams
 - Increase webcam open timeout to 2 seconds. This increases compatibility with random nobrand cameras.
 - Adds new `OpenCvWebcam` interface which exposes some additional functionality for webcams
     - Instances can be obtained from `OpenCvCameraFactory`, just like other types
     - Support for exposure control & focus control, using the SDK UVC driver's built-in interfaces
 - Adds new samples demonstrating some of the new functionality
 - Library version now printed to logcat when creating camera instance
 - Misc. bug fixes

### v1.3.2

 - Resolutions >480p are now possible with webcams (at reduced framerates)
 - Add exposure compensation and autoexposure lock APIs for internal camera
 - Fix blank display when user pipeline returned cropped mat of type CV_8UC1 (e.g. masks)
 - Print supported resolutions when user selects illegal resolution for camera

### v1.3.1

 - Transitive dependency on OpenCV-Repackged updated to 4.1.0-B, which drastically improves error handling when loading native library

### v1.3

 - Add official support for multiple concurrent camera streams (was possible before but required manual activity UI modifications)
   - Also allows for running Vuforia alongside EasyOpenCV
 - Add "TrackerAPI" classes (ability to run multiple OpenCV algorithms in the same pipeline, and switch between which output is rendered to the screen in realtime by tapping the viewport)
 - Add support for rendering cropped returns from user pipeline
 - A little internal code cleanup
 - Optimise viewport to re-use existing framebuffer memory
 - Fix issue where if a user pipeline created a submat from the input Mat, the submat would be de-linked from the input buffer on the next frame
 - Added ability to use some advanced features for internal cameras:
   - Added ability to set "recording hint"
   - Added ability to set "hardware frame timing range"
   - Added ability to control zoom
   - Added ability to control flashlight
   - Added support for using double buffering (default; can improve FPS)
 - API change: camera instances are now created by invoking `OpenCvCameraFactory.getInstance().create...`
 - Add examples:
   - InternalCameraAdvancedFeaturesExample
   - MultipleCameraExample
   - MultipleCameraExampleOpenCvAlongsideVuforia
   - TrackerApiExample

### v1.2

 - **HOTFIX:** implement workaround for SDK bug of RenderScript failing to initialize on some devices which prevented the webcam frames from being forwarded through the JNI to the Java side (See issue #1)

### v1.1

 - SDK v5.1 or higher now required
 - Add support for stream preview on Driver Station
 - Fix bug where internal camera was not correctly released
 - Fix bug where a null pipeline caused a crash
 - API change: user pipelines now need to `extends OpenCvPipeline` instead of `implements OpenCvPipeline`
 - Add ability for user pipeline to override `onViewportTapped()` to be notified if the user taps the viewport
 - Add `PipelineStageSwitchingExample` to show how to use `onViewportTapped()` to change which stage of your pipeline is drawn to the viewport for debugging purposes. It also shows how to get data from your pipeline to your OpMode.

### v1.0

 - Initial release
