package com.whelksoft.camera_with_rtmp

import android.app.Activity
import android.hardware.camera2.CameraAccessException
import android.media.CamcorderProfile
import android.os.Build
import androidx.annotation.RequiresApi
import com.pedro.rtplibrary.rtmp.RtmpCamera2
import com.whelksoft.camera_with_rtmp.CameraPermissions.ResultCallback
import com.whelksoft.camera_with_rtmp.CameraUtils.computeBestPreviewSize
import com.whelksoft.camera_with_rtmp.CameraUtils.getBestAvailableCamcorderProfileForResolutionPreset
import io.flutter.Log
import io.flutter.plugin.common.BinaryMessenger
import io.flutter.plugin.common.EventChannel
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.view.TextureRegistry
import net.ossrs.rtmp.ConnectCheckerRtmp
import java.util.*

const val TAG = "MethodCallHandlerImpl"

internal class MethodCallHandlerImpl(
    private val activity: Activity,
    private val messenger: BinaryMessenger,
    private val cameraPermissions: CameraPermissions,
    private val permissionsRegistry: PermissionStuff,
    private val textureRegistry: TextureRegistry
) : MethodCallHandler {
    private var flutterSurfaceTexture: TextureRegistry.SurfaceTextureEntry? = null
    private var camera: RtmpCamera2? = null
    private var streamProfile: CamcorderProfile? = null

    private val methodChannel: MethodChannel
    private val imageStreamChannel: EventChannel

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    override fun onMethodCall(call: MethodCall, result: MethodChannel.Result) {
        when (call.method) {
            "availableCameras" -> try {
                result.success(CameraUtils.getAvailableCameras(activity))
            } catch (e: Exception) {
                handleException(e, result)
            }
            "initialize" -> {
                stopCamera()
                cameraPermissions.requestPermissions(
                    activity,
                    permissionsRegistry,
                    call.argument("enableAudio")!!,
                    object : ResultCallback {
                        override fun onResult(errorCode: String?, errorDescription: String?) {
                            if (errorCode == null) {
                                try {
                                    instantiateCamera(call, result)
                                } catch (e: Exception) {
                                    handleException(e, result)
                                }
                            } else {
                                result.error(errorCode, errorDescription, null)
                            }
                        }
                    })
            }
            "takePicture" -> {
//                camera!!.takePicture(call.argument("path")!!, result)
            }
            "prepareForVideoRecording" -> {
                // This optimization is not required for Android.
                result.success(null)
            }
            "startVideoRecording" -> {
//                camera!!.startVideoRecording(call.argument("filePath")!!, result)
            }
            "startVideoStreaming" -> {
                Log.i("Stuff", call.arguments.toString())
                var bitrate: Int? = null
                if (call.hasArgument("bitrate")) {
                    bitrate = call.argument("bitrate")
                }
                val rotation = call.argument("rotation") ?: 90

                if (camera!!.prepareAudio() && camera!!.prepareVideo(
                        streamProfile!!.videoFrameWidth,
                        streamProfile!!.videoFrameHeight,
                        streamProfile!!.videoFrameRate,
                        bitrate!!,
                        rotation
                    )
                ) {
                    camera!!.startStream(call.argument("url"))
                    result.success(null)
                } else {
                    result.error("PrepareEncodeFailed", null, null)
                }
            }
            "startVideoRecordingAndStreaming" -> {
//                Log.i("Stuff", call.arguments.toString())
//                var bitrate: Int? = null
//                if (call.hasArgument("bitrate")) {
//                    bitrate = call.argument("bitrate")
//                }
//                camera!!.startVideoRecordingAndStreaming(
//                    call.argument("filePath")!!,
//                    call.argument("url"),
//                    bitrate,
//                    result
//                )
            }
            "pauseVideoStreaming" -> {
//                camera!!.pauseVideoStreaming(result)
            }
            "resumeVideoStreaming" -> {
//                camera!!.resumeVideoStreaming(result)
            }
            "stopRecordingOrStreaming" -> {
                camera!!.stopStream()
                result.success(null)
            }
            "stopRecording" -> {
//                camera!!.stopVideoRecording(result)
            }
            "stopStreaming" -> {
                camera!!.stopStream()
                result.success(null)
            }
            "pauseVideoRecording" -> {
//                camera!!.pauseVideoRecording(result)
            }
            "resumeVideoRecording" -> {
//                camera!!.resumeVideoRecording(result)
            }
            "startImageStream" -> {
//                try {
//                    camera!!.startPreviewWithImageStream(imageStreamChannel)
//                    result.success(null)
//                } catch (e: Exception) {
//                    handleException(e, result)
//                }
            }
            "stopImageStream" -> {
//                try {
//                    camera!!.startPreview()
//                    result.success(null)
//                } catch (e: Exception) {
//                    handleException(e, result)
//                }
            }
            "getStreamStatistics" -> {
//                try {
//                    camera!!.getStreamStatistics(result)
//                } catch (e: Exception) {
//                    handleException(e, result)
//                }
            }
            "dispose" -> {
                stopCamera()
                flutterSurfaceTexture?.release()
                result.success(null)
            }
            else -> result.notImplemented()
        }
    }

    private fun stopCamera() {
        if (camera != null) {
            camera!!.stopStream()
            camera!!.stopPreview()
        }
    }

    fun stopListening() {
        methodChannel.setMethodCallHandler(null)
    }

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    @Throws(CameraAccessException::class)
    private fun instantiateCamera(call: MethodCall, result: MethodChannel.Result) {
        val cameraName = call.argument<String>("cameraName")
        val resolutionPreset = call.argument<String>("resolutionPreset")
        val streamingPreset = call.argument<String>("streamingPreset")
        val enableAudio = call.argument<Boolean>("enableAudio")!!
        var enableOpenGL = false
        if (call.hasArgument("enableAndroidOpenGL")) {
            enableOpenGL = call.argument<Boolean>("enableAndroidOpenGL")!!
        }
        flutterSurfaceTexture = textureRegistry.createSurfaceTexture()
        val dartMessenger = DartMessenger(messenger, flutterSurfaceTexture!!.id())

        val isPortrait = true
        val preset = Camera.ResolutionPreset.valueOf(streamingPreset!!)
        streamProfile = getBestAvailableCamcorderProfileForResolutionPreset(
            cameraName!!,
            preset
        )

        val surfaceTexture = flutterSurfaceTexture!!.surfaceTexture()
        val previewSize = computeBestPreviewSize(cameraName, preset)
        if (isPortrait) {
            surfaceTexture.setDefaultBufferSize(previewSize.width, previewSize.height)
        } else {
            surfaceTexture.setDefaultBufferSize(previewSize.height, previewSize.width)
        }

        camera = RtmpCamera2(
            surfaceTexture,
            activity.applicationContext,
            enableOpenGL,
            object : ConnectCheckerRtmp {
                override fun onConnectionSuccessRtmp() {
                    Log.i(TAG, "onConnectionSuccessRtmp")
                }

                override fun onConnectionFailedRtmp(reason: String) {
                    Log.i(TAG, "onConnectionFailedRtmp: $reason")
                }

                override fun onNewBitrateRtmp(bitrate: Long) {
                    Log.i(TAG, "onNewBitrateRtmp: $bitrate")
                }

                override fun onDisconnectRtmp() {
                    Log.i(TAG, "onDisconnectRtmp")
                }

                override fun onAuthErrorRtmp() {
                    Log.i(TAG, "onAuthErrorRtmp")
                }

                override fun onAuthSuccessRtmp() {
                    Log.i(TAG, "onAuthSuccessRtmp")
                }
            })

        try {
            camera!!.startPreview(cameraName)
        } catch (e: CameraAccessException) {
            result.error("CameraAccess", e.message, null)
            return
        }

        val reply: MutableMap<String, Any> = HashMap()
        reply["textureId"] = flutterSurfaceTexture!!.id()

        if (isPortrait) {
            reply["previewWidth"] = previewSize.width
            reply["previewHeight"] = previewSize.height
        } else {
            reply["previewWidth"] = previewSize.height
            reply["previewHeight"] = previewSize.width
        }

        val orientation = 90
        reply["previewQuarterTurns"] = orientation / 90
        android.util.Log.i(
            TAG,
            "open: width: " + reply["previewWidth"] + " height: " + reply["previewHeight"] + " currentOrientation: " + orientation + " quarterTurns: " + reply["previewQuarterTurns"]
        )
        result.success(reply)
    }

    // We move catching CameraAccessException out of onMethodCall because it causes a crash
    // on plugin registration for sdks incompatible with Camera2 (< 21). We want this plugin to
    // to be able to compile with <21 sdks for apps that want the camera and support earlier version.
    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    private fun handleException(exception: Exception, result: MethodChannel.Result) {
        if (exception is CameraAccessException) {
            result.error("CameraAccess", exception.message, null)
        }
        throw (exception as RuntimeException)
    }

    init {
        methodChannel = MethodChannel(messenger, "plugins.flutter.io/camera_with_rtmp")
        imageStreamChannel =
            EventChannel(messenger, "plugins.flutter.io/camera_with_rtmp/imageStream")
        methodChannel.setMethodCallHandler(this)
    }
}