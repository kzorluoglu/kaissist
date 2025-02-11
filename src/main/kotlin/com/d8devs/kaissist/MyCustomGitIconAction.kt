package com.d8devs.kaissist.actions

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonSyntaxException
import com.google.gson.annotations.SerializedName
import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.notification.Notifications
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.VcsDataKeys
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.ChangeListManager
import com.intellij.vcs.commit.CommitWorkflowUi
import git4idea.GitUtil
import git4idea.commands.Git
import git4idea.commands.GitCommand
import git4idea.commands.GitLineHandler
import git4idea.repo.GitRepository
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

class MyCustomGitIconAction : DumbAwareAction() {
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    override fun update(event: AnActionEvent) {
        val commitWorkflowUi = event.getData(VcsDataKeys.COMMIT_WORKFLOW_UI)
        event.presentation.isEnabled = commitWorkflowUi != null
        event.presentation.text = "Generate Commit Message (Ollama)"
    }


    override fun actionPerformed(event: AnActionEvent) {
        val project: Project = event.project ?: return
        val commitWorkflowUi = event.getData(VcsDataKeys.COMMIT_WORKFLOW_UI) ?: return
        val repository = GitUtil.getRepositoryManager(project).repositories.firstOrNull() ?: return

        ApplicationManager.getApplication().executeOnPooledThread {
            val branchName = repository.currentBranchName ?: "unknown-branch"
            val ticketNumber = extractTicketNumber(branchName)

            // Get selected changes from the commit dialog
            val selectedChanges = commitWorkflowUi.getIncludedChanges()

            if (selectedChanges.isEmpty()) {
                showNotification("Please select files to generate commit message", NotificationType.WARNING)
                return@executeOnPooledThread
            }

            val gitDiff = generateDiff(repository, selectedChanges)
            if (gitDiff.isEmpty()) {
                return@executeOnPooledThread
            }

            val commitMessage = fetchCommitMessageFromOllama(gitDiff, ticketNumber)
            if (commitMessage.isNotEmpty()) {
                insertCommitMessage(project, commitWorkflowUi, commitMessage)
            }
        }
    }
    override fun getActionUpdateThread(): ActionUpdateThread {
        return ActionUpdateThread.EDT
    }

    private fun extractTicketNumber(branchName: String): String {
        val match = Regex("""\d+""").find(branchName)
        return match?.value ?: "UNKNOWN"
    }

    private fun generateDiff(repository: GitRepository, changes: Collection<Change>): String {
        val sb = StringBuilder()

        changes.forEach { change ->
            val beforePath = change.beforeRevision?.file?.path
            val afterPath = change.afterRevision?.file?.path

            // Get the correct file path based on change type
            val filePath = when (change.type) {
                Change.Type.DELETED -> beforePath
                else -> afterPath
            } ?: return@forEach

            try {
                when (change.type) {
                    Change.Type.NEW -> {
                        // For new files, get the actual content instead of diff
                        val file = change.afterRevision?.file
                        if (file != null) {
                            sb.appendLine("File: $filePath (new file)")
                            try {
                                val content = file.ioFile.readText()
                                    .lines()
                                    .filterNot { it.trim().isEmpty() } // Remove empty lines
                                    .joinToString("\n")
                                if (content.isNotEmpty()) {
                                    sb.appendLine("Content:")
                                    sb.appendLine(content)
                                }
                            } catch (e: IOException) {
                                showNotification("⚠️ Error reading file: ${e.message}", NotificationType.WARNING)
                            }
                        }
                    }
                    else -> {
                        // For modified files, get the diff
                        val handler = GitLineHandler(repository.project, repository.root, GitCommand.DIFF)
                        handler.setSilent(true)
                        handler.addParameters("--no-color", "--unified=3")

                        // Add specific parameters based on change type
                        when (change.type) {
                            Change.Type.DELETED -> handler.addParameters(filePath)
                            Change.Type.MOVED -> {
                                if (beforePath != null && afterPath != null) {
                                    handler.addParameters(beforePath, afterPath)
                                } else {
                                    handler.addParameters(filePath)
                                }
                            }
                            Change.Type.MODIFICATION -> handler.addParameters(filePath)
                            else -> {} // NEW case is handled above
                        }

                        // Run the diff command
                        val result = Git.getInstance().runCommand(handler)

                        if (result.success()) {
                            val diffOutput = result.outputAsJoinedString
                                .lines()
                                .dropWhile { it.startsWith("diff --git") || it.startsWith("index") }
                                .dropWhile { it.startsWith("---") || it.startsWith("+++") }
                                .filterNot { it.trim().isEmpty() }
                                .joinToString("\n")

                            if (diffOutput.isNotEmpty()) {
                                sb.appendLine("File: $filePath (${change.type.name.lowercase()})")
                                sb.appendLine("Changes:")
                                sb.appendLine(diffOutput)
                            }
                        } else {
                            showNotification("⚠️ Diff command failed: ${result.errorOutputAsJoinedString}", NotificationType.WARNING)
                        }
                    }
                }
                sb.appendLine("---")
            } catch (e: Exception) {
                showNotification("⚠️ Error processing file $filePath: ${e.message}", NotificationType.WARNING)
            }
        }

        val finalDiff = sb.toString().trim()
        if (finalDiff.isEmpty()) {
            showNotification("No changes detected in selected files", NotificationType.WARNING)
            return ""
        }

        // Log the final diff for debugging
        showNotification("Diff generated successfully: $finalDiff", NotificationType.INFORMATION)
        return finalDiff
    }

    private fun fetchCommitMessageFromOllama(diff: String, ticketNumber: String): String {
        val ollamaEndpoint = "http://localhost:11434/api/generate"

        // Only proceed if we have actual diff content
        if (diff.isEmpty() || diff.startsWith("⚠️")) {
            showNotification("No valid diff content found", NotificationType.WARNING)
            return ""
        }

        val prompt = """
        As a git commit message generator, analyze this git diff and create a concise, descriptive commit message:
        
        Git Diff:
        $diff
        
        Rules:
        1. Start with exactly "ref #$ticketNumber: "
        2. Follow with a clear, concise description
        3. Use present tense
        4. Be specific about what changed
        
        Generate the commit message now:
    """.trimIndent()

        val gson: Gson = GsonBuilder().disableHtmlEscaping().create()
        val jsonPayload = gson.toJson(
            mapOf(
                "model" to "llama3.2:latest",
                "prompt" to prompt,
                "stream" to false
            )
        )

        val requestBody = jsonPayload.toRequestBody("application/json".toMediaTypeOrNull())
        val request = Request.Builder()
            .url(ollamaEndpoint)
            .post(requestBody)
            .build()

        return try {
            httpClient.newCall(request).execute().use { response ->
                val responseBody = response.body?.string() ?: ""

                if (response.isSuccessful) {
                    val ollamaResponse = gson.fromJson(responseBody, OllamaResponse::class.java)
                    val message = ollamaResponse.commitMessage.trim()

                    // Ensure the message starts with the correct reference
                    if (!message.startsWith("ref #")) {
                        "ref #$ticketNumber: $message"
                    } else {
                        message
                    }
                } else {
                    showNotification("❌ Ollama request failed: ${response.message}", NotificationType.ERROR)
                    ""
                }
            }
        } catch (e: Exception) {
            showNotification("❌ Error: ${e.message}", NotificationType.ERROR)
            ""
        }
    }

    private fun insertCommitMessage(project: Project, commitWorkflowUi: CommitWorkflowUi, message: String) {
        WriteCommandAction.runWriteCommandAction(project) {
            commitWorkflowUi.commitMessageUi.setText(message)
        }
    }

    private fun showNotification(message: String, type: NotificationType) {
        Notifications.Bus.notify(
            Notification("MyCustomGitIconAction", "Commit Message Generation", message, type)
        )
    }

    data class OllamaResponse(
        @SerializedName("response") val commitMessage: String
    )
}
