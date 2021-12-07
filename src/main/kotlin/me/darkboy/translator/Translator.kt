package me.darkboy.translator

import de.linus.deepltranslator.DeepLConfiguration
import de.linus.deepltranslator.DeepLTranslator
import de.linus.deepltranslator.SourceLanguage
import de.linus.deepltranslator.TargetLanguage
import me.darkboy.translator.utils.WavFile
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONObject
import java.io.File

const val SPLIT_FILE_LENGTH_MS = 8000
const val WIT_TOKEN = "YOUR_WIT_API_TOKEN"

fun main() {

    val deepLConfiguration = DeepLConfiguration.Builder()
        .setRepetitions(0)
        .build()

    val deepLTranslator = DeepLTranslator(deepLConfiguration)

    val fileName = "Test.wav"

    for (file in splitWav(File(fileName))) {
        val messages = transcript(File(fileName))

        for (message in messages) {
            if (message.isNotEmpty() && message.isNotBlank()) {
                translate(deepLTranslator, message, SourceLanguage.JAPANESE, TargetLanguage.ENGLISH_AMERICAN)
            }
        }
    }
}

private fun transcript(soundFile: File): List<String> {

    val transcriptions = ArrayList<String>()

    val client = OkHttpClient()

    val httpUrl = HttpUrl.Builder()
        .scheme("https")
        .host("api.wit.ai")
        .addPathSegment("speech")
        .addQueryParameter(
            "v",
            "20211127"
        )
        .build()

    val request = Request.Builder()
        .post(soundFile.asRequestBody("audio/wav".toMediaTypeOrNull()))
        .header("Authorization", "Bearer $WIT_TOKEN")
        .header("Content-Type", "audio/wav")
        .url(httpUrl)
        .build()

    val responseBody = client.newCall(request).execute().body!!.string()

    val lines: List<String> = responseBody.split(System.getProperty("line.separator"))

    val jsonData = JSONObject(lines[lines.lastIndex])

    val transcription = jsonData.optString("text")

    transcriptions.add(transcription)

    return transcriptions
}

private fun splitWav(input: File): List<File> {
    val splittedFiles = ArrayList<File>()

    val inputWavFile = WavFile.openWavFile(input)

    val numChannels = inputWavFile.numChannels

    val maxFramesPerFile = inputWavFile.sampleRate.toInt() * SPLIT_FILE_LENGTH_MS / 1000

    val buffer = DoubleArray(maxFramesPerFile * numChannels)

    var framesRead: Int
    var fileCount = 0

    do {
        // Read frames into buffer
        framesRead = inputWavFile.readFrames(buffer, maxFramesPerFile)
        val outputWavFile = WavFile.newWavFile(
            File("out" + (fileCount + 1) + ".wav"),
            inputWavFile.numChannels,
            framesRead.toLong(),
            inputWavFile.validBits,
            inputWavFile.sampleRate
        )

        // Write the buffer
        outputWavFile.writeFrames(buffer, framesRead)
        outputWavFile.close()


        fileCount++

        splittedFiles.add(outputWavFile.file)

    } while (framesRead != 0)

    inputWavFile.close()

    return splittedFiles
}

private fun translate(deepLTranslator: DeepLTranslator, message: String, sourceLanguage: SourceLanguage, targetLanguage: TargetLanguage) {
    deepLTranslator.translateAsync(message, sourceLanguage, targetLanguage)
        .whenComplete { res: String?, ex: Throwable? ->
            if (ex != null) {
                ex.printStackTrace()
            } else {
                println(res)
            }
        }
}