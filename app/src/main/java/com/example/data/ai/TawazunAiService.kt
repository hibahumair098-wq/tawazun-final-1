package com.example.data.ai

import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withTimeout

private const val TAG = "TawazunAiService"

class TawazunAiService {

    // Lazy initialization of Gemini 3.8 Flash model via Firebase AI Logic
    private val model by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                temperature = 0.7f
                topP = 0.95f
            }
        )
    }

    /**
     * AI Homework Explainer for Pakistani Students (Matric & O-Levels).
     * Streams explanation step-by-step.
     */
    fun explainHomework(subject: String, topicOrQuestion: String): Flow<String> = flow {
        val prompt = """
            You are 'Ustad AI' (استاد اے آئی), a patient, encouraging academic tutor for Pakistani school students (Matric and O-Levels).
            The student is working on their $subject homework:
            Question/Topic: "$topicOrQuestion"
            
            Provide a clear, step-by-step conceptual explanation with simple examples.
            If relevant, you may include brief Urdu terms in parentheses (e.g. 'دو درجی مساوات' for Quadratic Equation).
            Keep it focused, educational, and structured so the student learns how to solve it themselves without just giving them a shortcut to copy.
        """.trimIndent()

        val accumulated = StringBuilder()
        try {
            withTimeout(60_000L) {
                model.generateContentStream(prompt).collect { chunk ->
                    val text = chunk.text.orEmpty()
                    accumulated.append(text)
                    emit(accumulated.toString())
                }
            }
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Gemini AI generation timed out", e)
            throw IllegalStateException("AI tutor timed out after 60 seconds. Please check your connection and try again.")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Gemini AI generation failed", e)
            throw e
        }
    }

    /**
     * AI Screen-Time & Study Balance Advisor for Parents.
     */
    fun getParentingAdvice(query: String): Flow<String> = flow {
        val prompt = """
            You are a family counselor and digital wellness advisor specializing in Pakistani households.
            A parent asks: "$query"
            
            Provide empathetic, practical, and culturally nuanced advice considering the reality of Pakistani students (WhatsApp school groups, Board exam pressure, tuition schedules, and screen-time balancing).
            Format with clear bullet points.
        """.trimIndent()

        val accumulated = StringBuilder()
        try {
            withTimeout(60_000L) {
                model.generateContentStream(prompt).collect { chunk ->
                    val text = chunk.text.orEmpty()
                    accumulated.append(text)
                    emit(accumulated.toString())
                }
            }
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Gemini AI advice timed out", e)
            throw IllegalStateException("AI advice request timed out. Please try again.")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Gemini AI advice failed", e)
            throw e
        }
    }
}
