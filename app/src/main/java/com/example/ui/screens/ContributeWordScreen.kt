package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Publish
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.ContributeSubmitState
import com.example.ui.MainViewModel
import com.example.ui.theme.GlassCard
import com.example.ui.theme.GlassPillChip
import com.example.ui.theme.LocalGlassTokens

private val CONTRIBUTE_CATEGORIES = listOf(
    "Society & Culture",
    "Governance & Law",
    "Conflict & Liberation",
    "Faith & Heritage",
    "Economy & Land",
    "Eras & Civilizations"
)

private val CONTRIBUTE_DIALECTS = listOf(
    "Kabyle (Kb.)",
    "Touareg (Tq.)",
    "Mozabite (Mz.)",
    "Ghadamès (Gd.)",
    "Tashelhit",
    "Riffian",
    "Pan-Amazigh"
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ContributeWordScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val contributeState by viewModel.contributeState.collectAsStateWithLifecycle()
    val tokens = LocalGlassTokens.current

    var tamazightTerm by remember { mutableStateOf("") }
    var tamazightPlural by remember { mutableStateOf("") }
    var tifinagh by remember { mutableStateOf("") }
    var arabicMeaning by remember { mutableStateOf("") }
    var frenchMeaning by remember { mutableStateOf("") }
    var englishMeaning by remember { mutableStateOf("") }
    var exampleNote by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf(CONTRIBUTE_CATEGORIES.first()) }
    var selectedDialect by remember { mutableStateOf(CONTRIBUTE_DIALECTS.first()) }

    val isBusy = contributeState is ContributeSubmitState.Assisting ||
        contributeState is ContributeSubmitState.Submitting

    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = Modifier
                .widthIn(max = 720.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 116.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                elevated = true,
                contentPadding = 18.dp
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Enrich the Communal Lexicon",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Share Tamazight vocabulary you know across Arabic, French, and English to build an open, living heritage repository.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    OutlinedTextField(
                        value = tamazightTerm,
                        onValueChange = {
                            tamazightTerm = it
                            if (contributeState !is ContributeSubmitState.Idle) viewModel.resetContributeState()
                        },
                        label = { Text("Tamazight Word / Expression *") },
                        placeholder = { Text("e.g., Tilelli, Tamurt, Amezruy") },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("contribute_tamazight_input")
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedTextField(
                            value = tamazightPlural,
                            onValueChange = { tamazightPlural = it },
                            label = { Text("Plural Form") },
                            placeholder = { Text("e.g., tilelliyin") },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("contribute_plural_input")
                        )
                        OutlinedTextField(
                            value = tifinagh,
                            onValueChange = { tifinagh = it },
                            label = { Text("Neo-Tifinagh") },
                            placeholder = { Text("ⵜⵉⵍⴻⵍⵍⵉ") },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("contribute_tifinagh_input")
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedButton(
                        onClick = {
                            viewModel.assistContributeTranslations(
                                tamazightTerm = tamazightTerm,
                                frenchHint = frenchMeaning,
                                contextHint = exampleNote
                            ) { suggestion ->
                                if (tamazightTerm.isBlank()) tamazightTerm = suggestion.tamazightTerm
                                if (tamazightPlural.isBlank()) tamazightPlural = suggestion.tamazightPlural
                                if (tifinagh.isBlank()) tifinagh = suggestion.tifinagh
                                if (arabicMeaning.isBlank()) arabicMeaning = suggestion.arabicMeaning
                                if (frenchMeaning.isBlank()) frenchMeaning = suggestion.frenchMeaning
                                if (englishMeaning.isBlank()) englishMeaning = suggestion.englishMeaning
                                if (exampleNote.isBlank()) exampleNote = suggestion.etymologyNote
                            }
                        },
                        enabled = !isBusy,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 48.dp)
                            .testTag("contribute_ai_assist_button")
                    ) {
                        if (contributeState is ContributeSubmitState.Assisting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Generating 4-Language Draft…")
                        } else {
                            Icon(
                                imageVector = Icons.Rounded.AutoAwesome,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("AI Multilingual Assist (AR • FR • EN • Tifinagh)")
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = arabicMeaning,
                        onValueChange = { arabicMeaning = it },
                        label = { Text("Arabic Meaning (العربية) *") },
                        placeholder = { Text("e.g., الحرية") },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("contribute_arabic_input")
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = frenchMeaning,
                        onValueChange = { frenchMeaning = it },
                        label = { Text("French Meaning (Français) *") },
                        placeholder = { Text("e.g., Liberté") },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("contribute_french_input")
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = englishMeaning,
                        onValueChange = { englishMeaning = it },
                        label = { Text("English Meaning *") },
                        placeholder = { Text("e.g., Freedom / Liberty") },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("contribute_english_input")
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "Dialect / Variant",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CONTRIBUTE_DIALECTS.forEach { d ->
                            GlassPillChip(
                                label = d,
                                selected = selectedDialect == d,
                                onClick = { selectedDialect = d }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "Historical / Thematic Category",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        CONTRIBUTE_CATEGORIES.forEach { cat ->
                            GlassPillChip(
                                label = cat,
                                selected = selectedCategory == cat,
                                onClick = { selectedCategory = cat }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = exampleNote,
                        onValueChange = { exampleNote = it },
                        label = { Text("Usage Example / Etymology Note") },
                        placeholder = { Text("Optional historical context, root, or example sentence") },
                        minLines = 2,
                        maxLines = 4,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("contribute_note_input")
                    )

                    AnimatedVisibility(visible = contributeState is ContributeSubmitState.Error) {
                        val msg = (contributeState as? ContributeSubmitState.Error)?.message ?: ""
                        Text(
                            text = msg,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(top = 10.dp)
                        )
                    }

                    AnimatedVisibility(visible = contributeState is ContributeSubmitState.Success) {
                        val msg = (contributeState as? ContributeSubmitState.Success)?.message ?: ""
                        GlassCard(
                            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                            contentPadding = 12.dp
                        ) {
                            Text(
                                text = msg,
                                style = MaterialTheme.typography.bodyMedium,
                                color = tokens.arabicAccent,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = {
                            viewModel.submitCommunityWord(
                                tamazightTerm = tamazightTerm,
                                tamazightPlural = tamazightPlural,
                                tifinagh = tifinagh,
                                arabicMeaning = arabicMeaning,
                                frenchMeaning = frenchMeaning,
                                englishMeaning = englishMeaning,
                                dialect = selectedDialect,
                                category = selectedCategory,
                                exampleNote = exampleNote,
                                onSubmitted = {
                                    tamazightTerm = ""
                                    tamazightPlural = ""
                                    tifinagh = ""
                                    arabicMeaning = ""
                                    frenchMeaning = ""
                                    englishMeaning = ""
                                    exampleNote = ""
                                }
                            )
                        },
                        enabled = !isBusy,
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 54.dp)
                            .testTag("contribute_submit_button")
                    ) {
                        if (contributeState is ContributeSubmitState.Submitting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Publishing to Communal Lexicon…")
                        } else {
                            Icon(
                                imageVector = Icons.Rounded.Publish,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Publish to Communal Database",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}
