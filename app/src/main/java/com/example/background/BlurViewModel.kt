package com.example.background

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.work.*
import com.example.background.workers.BlurWorker
import com.example.background.workers.CleanupWorker
import com.example.background.workers.SaveImageToFileWorker


class BlurViewModel(application: Application) : AndroidViewModel(application) {
    private val workManager = WorkManager.getInstance(application)
    internal var imageUri: Uri? = null
    internal var outputUri: Uri? = null
    internal val outputWorkInfos: LiveData<List<WorkInfo>>

    init {
        // This transformation makes sure that whenever the current work Id changes the WorkInfo
        // the UI is listening to changes
        outputWorkInfos = workManager.getWorkInfosByTagLiveData(TAG_OUTPUT)
    }

    /*
    old code for OneTimeWorkRequest
     */
//    internal fun applyBlur(blurLevel: Int) {
//       val blurRequest = OneTimeWorkRequestBuilder<BlurWorker>()
//               .setInputData(createInputDataForUri()).build()
//        workManager.enqueue(blurRequest)
//    }

    internal fun applyBlur(blurLevel: Int) {
        /*Add WorkRequest to Cleanup temporary images */

        //1. beginWith:
//        var continuation = workManager
//                .beginWith(OneTimeWorkRequest
//                        .from(CleanupWorker::class.java))

/*
1.Ensure that your chain of work to blur your file is unique by using beginUniqueWork.
2.ExistingWorkPolicy. Your options are REPLACE, KEEP or APPEND.
3.REPLACE because if the user decides to blur another image before the current one is finished
         , we want to stop the current one and start blurring the new image
 */

        //2. beginUniqueWork:
        var continuation = workManager
                .beginUniqueWork(
                        IMAGE_MANIPULATION_WORK_NAME,
                        ExistingWorkPolicy.REPLACE,
                        OneTimeWorkRequest.from(CleanupWorker::class.java)
                )

        // Add WorkRequest to blur the image
        for (i in 0 until blurLevel) {
            val blurBuilder = OneTimeWorkRequestBuilder<BlurWorker>()

            // Input the Uri if this is the first blur operation
            // After the first blur operation the input will be the output of previous
            // blur operations.
            if (i == 0) {
                blurBuilder.setInputData(createInputDataForUri())
            }
            continuation = continuation.then(blurBuilder.build())
        }

        // Put this inside the applyBlur() function
        // Create charging constraint
        val constraints = Constraints.Builder()
                .setRequiresCharging(true)
                .build()

        // Add WorkRequest to save the image to the filesystem
        val save = OneTimeWorkRequestBuilder<SaveImageToFileWorker>()
                .setConstraints(constraints)
                .addTag(TAG_OUTPUT)
                //You'll be tagging the SaveImageToFileWorker WorkRequest, so that you can get it using getWorkInfosByTag.
                .build()

        continuation = continuation.then(save)

        // Actually start the work
        continuation.enqueue()
    }

    private fun uriOrNull(uriString: String?): Uri? {
        return if (!uriString.isNullOrEmpty()) {
            Uri.parse(uriString)
        } else {
            null
        }
    }

    /**
     * Setters
     */
    internal fun setImageUri(uri: String?) {
        imageUri = uriOrNull(uri)
    }

    internal fun setOutputUri(outputImageUri: String?) {
        outputUri = uriOrNull(outputImageUri)
    }

    /**
     * Creates the input data bundle which includes the Uri to operate on
     * @return Data which contains the Image Uri as a String
     */
    private fun createInputDataForUri(): Data {
        val builder = Data.Builder()
        imageUri?.let {
            builder.putString(KEY_IMAGE_URI, imageUri.toString())
        }
        return builder.build()
    }

    internal fun cancelWork() {
        workManager.cancelUniqueWork(IMAGE_MANIPULATION_WORK_NAME)
    }
}
