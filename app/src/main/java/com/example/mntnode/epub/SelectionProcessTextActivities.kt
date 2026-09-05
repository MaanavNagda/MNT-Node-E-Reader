package com.example.mntnode.epub

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.speech.tts.TextToSpeech
import java.util.Locale

abstract class BaseSelectionProcessTextActivity : Activity() {
    protected fun selectedText(): String =
        intent?.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString()?.trim().orEmpty()
}

class HighlightProcessTextActivity : BaseSelectionProcessTextActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val result = Intent().putExtra(Intent.EXTRA_PROCESS_TEXT, selectedText())
        setResult(RESULT_OK, result)
        finish()
    }
}

class GoogleProcessTextActivity : BaseSelectionProcessTextActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val text = selectedText()
        if (text.isNotBlank()) {
            val url = "https://www.google.com/search?q=${Uri.encode(text)}"
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }
        setResult(RESULT_OK, Intent().putExtra(Intent.EXTRA_PROCESS_TEXT, text))
        finish()
    }
}

class FindProcessTextActivity : BaseSelectionProcessTextActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val text = selectedText()
        val result = Intent(ACTION_MNTNODE_FIND_SELECTION).putExtra(EXTRA_FIND_QUERY, text)
        sendBroadcast(result)
        setResult(RESULT_OK, Intent().putExtra(Intent.EXTRA_PROCESS_TEXT, text))
        finish()
    }
}

class ReadAloudProcessTextActivity : BaseSelectionProcessTextActivity(), TextToSpeech.OnInitListener {
    private var tts: TextToSpeech? = null
    private var text: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        text = selectedText()
        tts = TextToSpeech(this, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS && text.isNotBlank()) {
            tts?.language = Locale.getDefault()
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "selection_read_aloud")
        }
        setResult(RESULT_OK, Intent().putExtra(Intent.EXTRA_PROCESS_TEXT, text))
        finish()
    }

    override fun onDestroy() {
        tts?.shutdown()
        super.onDestroy()
    }
}

class DictionaryProcessTextActivity : BaseSelectionProcessTextActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val text = selectedText()
        if (text.isNotBlank()) {
            val url = "https://www.dictionary.com/browse/${Uri.encode(text)}"
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }
        setResult(RESULT_OK, Intent().putExtra(Intent.EXTRA_PROCESS_TEXT, text))
        finish()
    }
}

const val ACTION_MNTNODE_FIND_SELECTION = "com.example.mntnode.ACTION_FIND_SELECTION"
const val EXTRA_FIND_QUERY = "find_query"
