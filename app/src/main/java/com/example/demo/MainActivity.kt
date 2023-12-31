package com.example.demo

import android.app.ProgressDialog
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.text.TextUtils
import android.widget.Toast
import com.example.demo.databinding.ActivityMainBinding
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.io.IOException
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

class MainActivity : AppCompatActivity() {

    private val MODEL_ASSETS_PATH = "model.tflite"
    private val INPUT_MAXLEN = 50

    private var tfLiteInterpreter : Interpreter? = null

    lateinit var binding:ActivityMainBinding
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding=ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        // Init the classifier.
        val classifier = Classifier( this , "word_dict.json" , INPUT_MAXLEN )
        // Init TFLiteInterpreter
        tfLiteInterpreter = Interpreter( loadModelFile() )

        // Start vocab processing, show a ProgressDialog to the user.
        val progressDialog = ProgressDialog( this )
        progressDialog.setMessage( "Parsing word_dict.json ..." )
        progressDialog.setCancelable( false )
        progressDialog.show()
        classifier.processVocab( object: Classifier.VocabCallback {
            override fun onVocabProcessed() {
                // Processing done, dismiss the progressDialog.
                progressDialog.dismiss()
            }
        })

        binding.button.setOnClickListener {
            val text=binding.inputText.text.toString().lowercase().trim()
            if ( !TextUtils.isEmpty( text ) ){
                // Tokenize and pad the given input text.
                val tokenizedMessage = classifier.tokenize( text )
                val paddedMessage = classifier.padSequence( tokenizedMessage )

                val results = classifySequence( paddedMessage )
                // Assuming binary classification, use a threshold (e.g., 0.5) to decide the class
                val threshold = 0.5
                val predictedClass = if (results[0] > threshold) "Dark Pattern" else "Not a Dark Pattern"

                binding.output.text = "Prediction: $predictedClass"
            }else{
                Toast.makeText( this@MainActivity, "Please enter a message.", Toast.LENGTH_LONG).show();
            }

        }


    }

    @Throws(IOException::class)
    private fun loadModelFile(): MappedByteBuffer {

        val assetFileDescriptor = assets.openFd(MODEL_ASSETS_PATH)
        val fileInputStream = FileInputStream(assetFileDescriptor.fileDescriptor)
        val fileChannel = fileInputStream.channel
        val startOffset = assetFileDescriptor.startOffset
        val declaredLength = assetFileDescriptor.declaredLength
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }

    // Perform inference, given the input sequence.
    private fun classifySequence (sequence : IntArray ): FloatArray {
        // Input shape -> ( 1 , INPUT_MAXLEN )
        val inputs : Array<FloatArray> = arrayOf( sequence.map { it.toFloat() }.toFloatArray() )
        // Output shape -> ( 1 , 2 ) ( as numClasses = 2 )
        val outputs : Array<FloatArray> = arrayOf(FloatArray(1))
        tfLiteInterpreter?.run( inputs , outputs )
        return outputs[0]
    }
}