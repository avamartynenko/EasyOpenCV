/*
 * Copyright (c) 2019 OpenFTC Team
 *
 * Note: credit where credit is due - some parts of OpenCv's
 *       JavaCameraView were used as a reference
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package org.openftc.easyopencv;

import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;

import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.imgproc.Imgproc;

import java.io.IOException;
import java.util.List;

class OpenCvInternalCameraImpl extends OpenCvCameraBase implements Camera.PreviewCallback, OpenCvInternalCamera
{
    private Camera camera;
    private OpenCvInternalCamera.CameraDirection direction;
    private byte[] rawSensorBuffer;
    private Mat rawSensorMat;
    private Mat rgbMat;
    private SurfaceTexture bogusSurfaceTexture;
    private int maxZoom = -1;
    private volatile boolean isOpen = false;
    private volatile boolean isStreaming = false;

    public OpenCvInternalCameraImpl(OpenCvInternalCamera.CameraDirection direction)
    {
        this.direction = direction;
    }

    public OpenCvInternalCameraImpl(OpenCvInternalCamera.CameraDirection direction, int containerLayoutId)
    {
        super(containerLayoutId);
        this.direction = direction;
    }

    @Override
    public OpenCvCameraRotation getDefaultRotation()
    {
        return OpenCvCameraRotation.UPRIGHT;
    }

    @Override
    protected int mapRotationEnumToOpenCvRotateCode(OpenCvCameraRotation rotation)
    {
        /*
         * The camera sensor in a phone is mounted sideways, such that the raw image
         * is only upright when the phone is rotated to the left. Therefore, we need
         * to manually rotate the image if the phone is in any other orientation
         */

        if(direction == OpenCvInternalCamera.CameraDirection.BACK)
        {
            if(rotation == OpenCvCameraRotation.UPRIGHT)
            {
                return Core.ROTATE_90_CLOCKWISE;
            }
            else if(rotation == OpenCvCameraRotation.UPSIDE_DOWN)
            {
                return Core.ROTATE_90_COUNTERCLOCKWISE;
            }
            else if(rotation == OpenCvCameraRotation.SIDEWAYS_RIGHT)
            {
                return Core.ROTATE_180;
            }
            else
            {
                return -1;
            }
        }
        else if(direction == OpenCvInternalCamera.CameraDirection.FRONT)
        {
            if(rotation == OpenCvCameraRotation.UPRIGHT)
            {
                return Core.ROTATE_90_COUNTERCLOCKWISE;
            }
            else if(rotation == OpenCvCameraRotation.UPSIDE_DOWN)
            {
                return Core.ROTATE_90_CLOCKWISE;
            }
            else if(rotation == OpenCvCameraRotation.SIDEWAYS_RIGHT)
            {
                return Core.ROTATE_180;
            }
            else
            {
                return -1;
            }
        }

        return -1;
    }

    @Override
    public synchronized void openCameraDevice()
    {
        if(!isOpen)
        {
            if(camera == null)
            {
                camera = Camera.open(direction.id);
            }

            isOpen = true;
        }
    }

    @Override
    public synchronized void closeCameraDevice()
    {
        cleanupForClosingCamera();

        if(isOpen)
        {
            if(camera != null)
            {
                stopStreaming();
                camera.stopPreview();
                camera.release();
                camera = null;
            }

            isOpen = false;
        }
    }

    @Override
    public synchronized void startStreaming(int width, int height)
    {
        startStreaming(width, height, getDefaultRotation());
    }

    @Override
    public synchronized void startStreaming(int width, int height, OpenCvCameraRotation rotation)
    {
        if(!isOpen)
        {
            throw new OpenCvCameraException("startStreaming() called, but camera is not opened!");
        }

        /*
         * If we're already streaming, then that's OK, but we need to stop
         * streaming in the old mode before we can restart in the new one.
         */
        if(isStreaming)
        {
            stopStreaming();
        }

        /*
         * Prep the viewport
         */
        prepareForStartStreaming(width, height, rotation);

        rawSensorMat = new Mat(height + (height/2), width, CvType.CV_8UC1);
        rgbMat = new Mat(height + (height/2), width, CvType.CV_8UC1);

        if(camera != null)
        {
            Camera.Parameters parameters = camera.getParameters();
            parameters.setPreviewFormat(ImageFormat.NV21);
            parameters.setPreviewSize(width, height);

            /*
             * Not all cameras support all focus modes...
             */
            if(parameters.getSupportedFocusModes().contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO))
            {
                parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO);
            }
            else if(parameters.getSupportedFocusModes().contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE))
            {
                parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE);
            }
            else if(parameters.getSupportedFocusModes().contains(Camera.Parameters.FOCUS_MODE_FIXED))
            {
                parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_FIXED);
            }

            boolean isRequestedSizeSupported = false;

            List<Camera.Size> cameraSupportedPreviewSizes = parameters.getSupportedPreviewSizes();

            for(Camera.Size size : cameraSupportedPreviewSizes)
            {
                if(size.width == width && size.height == height)
                {
                    isRequestedSizeSupported = true;
                    break;
                }
            }

            if(!isRequestedSizeSupported)
            {
                throw new OpenCvCameraException("Camera does not support requested resolution!");
            }

            maxZoom = parameters.getMaxZoom();
            camera.setParameters(parameters);

            int pixels = width * height;
            int bufSize  = pixels * ImageFormat.getBitsPerPixel(parameters.getPreviewFormat()) / 8;
            rawSensorBuffer = new byte[bufSize];

            bogusSurfaceTexture = new SurfaceTexture(10);

            camera.setPreviewCallbackWithBuffer(this);
            camera.addCallbackBuffer(rawSensorBuffer);

            try
            {
                camera.setPreviewTexture(bogusSurfaceTexture);
            }
            catch (IOException e)
            {
                e.printStackTrace();
                //closeCameraDevice();
                return;
            }

            camera.startPreview();
            isStreaming = true;
        }
    }

    @Override
    public synchronized void stopStreaming()
    {
        if(!isOpen)
        {
            throw new OpenCvCameraException("stopStreaming() called, but camera is not opened!");
        }

        cleanupForEndStreaming();

        maxZoom = -1;

        if(camera != null)
        {
            camera.setPreviewCallback(null);
            camera.stopPreview();
        }

        if(rawSensorMat != null)
        {
            rawSensorMat.release();
            rawSensorMat = null;
        }

        if(rgbMat != null)
        {
            rgbMat.release();
            rgbMat = null;
        }

        isStreaming = false;
    }

    /*
     * This needs to be synchronized with stopStreamingImplSpecific()
     * because we touch objects that are destroyed in that method.
     */
    @Override
    public synchronized void onPreviewFrame(byte[] data, Camera camera)
    {
        notifyStartOfFrameProcessing();

        /*
         * Unfortunately, we can't easily create a Java byte[] that
         * references the native memory in a Mat, so we have to do
         * a memcpy from our Java byte[] to the native one in the Mat.
         * (If we could, then we could have the camera dump the preview
         * image directly into the Mat).
         *
         * TODO: investigate using a bit of native code to remove the need to do a memcpy
         */
        if(rawSensorMat != null)
        {
            rawSensorMat.put(0,0,data);

            Imgproc.cvtColor(rawSensorMat, rgbMat, Imgproc.COLOR_YUV2RGBA_NV21, 4);
            handleFrame(rgbMat);

            if(camera != null)
            {
                camera.addCallbackBuffer(rawSensorBuffer);
            }
        }
    }

    @Override
    public synchronized void setFlashlightEnabled(boolean enabled)
    {
        if(camera != null)
        {
            Camera.Parameters parameters = camera.getParameters();

            List<String> supportedFlashModes = parameters.getSupportedFlashModes();

            if(supportedFlashModes == null)
            {
                throw new OpenCvCameraException("Camera does not have a flash!");
            }
            else if(!supportedFlashModes.contains(Camera.Parameters.FLASH_MODE_TORCH))
            {
                throw new OpenCvCameraException("Camera flash does not support torch mode!");
            }

            if(enabled)
            {
                parameters.setFlashMode(Camera.Parameters.FLASH_MODE_TORCH);
            }
            else
            {
                parameters.setFlashMode(Camera.Parameters.FLASH_MODE_OFF);
            }

            camera.setParameters(parameters);
        }
    }

    @Override
    public synchronized int getMaxSupportedZoom()
    {
        if(camera != null)
        {
            if(maxZoom == -1)
            {
                throw new OpenCvCameraException("Cannot set zoom until streaming has been started");
            }

            return maxZoom;
        }
        return 0;
    }

    @Override
    public synchronized void setZoom(int zoom)
    {
        if(camera != null)
        {
            if(maxZoom == -1)
            {
                throw new OpenCvCameraException("Cannot set zoom until streaming has been started");
            }
            else if(zoom > maxZoom)
            {
                throw new OpenCvCameraException(String.format("Zoom value of %d requested, but maximum zoom supported in current configuration is %d", zoom, maxZoom));
            }
            else if(zoom < 0)
            {
                throw new OpenCvCameraException("Zoom value cannot be less than 0");
            }
            Camera.Parameters parameters = camera.getParameters();
            parameters.setZoom(zoom);
            camera.setParameters(parameters);
        }
    }

    @Override
    public synchronized void setRecordingHint(boolean hint)
    {
        Camera.Parameters parameters = camera.getParameters();
        parameters.setRecordingHint(hint);
        camera.setParameters(parameters);
    }

    @Override
    public synchronized void setHardwareFrameTimingRange(FrameTimingRange frameTiming)
    {
        Camera.Parameters parameters = camera.getParameters();
        parameters.setPreviewFpsRange(frameTiming.min*1000, frameTiming.max*1000);
        camera.setParameters(parameters);
    }

    @Override
    public synchronized FrameTimingRange[] getFrameTimingRangesSupportedByHardware()
    {
        Camera.Parameters parameters = camera.getParameters();
        List<int[]> rawRanges = parameters.getSupportedPreviewFpsRange();
        FrameTimingRange[] ranges = new FrameTimingRange[rawRanges.size()];

        for(int i = 0; i < ranges.length; i++)
        {
            int[] raw = rawRanges.get(i);
            ranges[i] = new FrameTimingRange(raw[0]/1000, raw[1]/1000);
        }

        return ranges;
    }
}