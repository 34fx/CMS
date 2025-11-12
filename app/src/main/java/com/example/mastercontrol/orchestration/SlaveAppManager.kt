package com.example.mastercontrol.orchestration

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import androidx.core.content.getSystemService
import androidx.core.content.pm.PackageInfoCompat
import com.example.mastercontrol.model.CommandEnvelope
import com.example.mastercontrol.model.CommandResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object SlaveAppManager {

    suspend fun install(context: Context, command: CommandEnvelope.Command.Install): CommandResult =
        withContext(Dispatchers.IO) {
            runCatching {
                val installer = context.packageManager.packageInstaller
                val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
                    setAppLabel(command.displayName)
                    setAppPackageName(command.packageName)
                }
                val sessionId = installer.createSession(params)
                installer.openSession(sessionId).use { session ->
                    val apkFile = File(command.sourcePath)
                    require(apkFile.exists()) { "APK missing: ${command.sourcePath}" }
                    apkFile.inputStream().use { input ->
                        session.openWrite("base", 0, apkFile.length()).use { output ->
                            input.copyTo(output)
                            session.fsync(output)
                        }
                    }
                    session.commit(createStatusIntent(context, command.packageName))
                }
                CommandResult.Success(command.id, "Install requested")
            }.getOrElse { throwable ->
                CommandResult.Error(command.id, throwable.message ?: "Install failed")
            }
        }

    suspend fun uninstall(context: Context, command: CommandEnvelope.Command.Uninstall): CommandResult =
        withContext(Dispatchers.IO) {
            runCatching {
                context.packageManager.packageInstaller.uninstall(
                    command.packageName,
                    createStatusIntent(context, command.packageName)
                )
                CommandResult.Success(command.id, "Uninstall requested")
            }.getOrElse { throwable ->
                CommandResult.Error(command.id, throwable.message ?: "Uninstall failed")
            }
        }

    suspend fun start(context: Context, command: CommandEnvelope.Command.Start): CommandResult =
        withContext(Dispatchers.IO) {
            runCatching {
                val launchIntent = context.packageManager.getLaunchIntentForPackage(command.packageName)
                    ?: error("Launch intent missing")
                launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(launchIntent)
                CommandResult.Success(command.id, "Start requested")
            }.getOrElse { throwable ->
                CommandResult.Error(command.id, throwable.message ?: "Start failed")
            }
        }

    suspend fun stop(context: Context, command: CommandEnvelope.Command.Stop): CommandResult =
        withContext(Dispatchers.IO) {
            runCatching {
                val activityManager = context.getSystemService<ActivityManager>()
                    ?: error("ActivityManager unavailable")
                activityManager.forceStopPackage(command.packageName)
                CommandResult.Success(command.id, "Stop requested")
            }.getOrElse { throwable ->
                CommandResult.Error(command.id, throwable.message ?: "Stop failed")
            }
        }

    suspend fun update(context: Context, command: CommandEnvelope.Command.Update): CommandResult =
        withContext(Dispatchers.IO) {
            val currentVersion = runCatching {
                val info = context.packageManager.getPackageInfo(command.packageName, 0)
                PackageInfoCompat.getLongVersionCode(info)
            }.getOrDefault(-1)

            if (currentVersion >= command.versionCode && currentVersion != -1L) {
                CommandResult.Success(command.id, "Already up to date")
            } else {
                install(
                    context,
                    CommandEnvelope.Command.Install(
                        id = command.id,
                        packageName = command.packageName,
                        sourcePath = command.sourcePath,
                        displayName = command.displayName
                    )
                )
            }
        }

    private fun createStatusIntent(context: Context, packageName: String): Intent {
        return Intent("com.example.mastercontrol.PACKAGE_STATUS").apply {
            setPackage(context.packageName)
            data = Uri.parse("package:$packageName")
        }
    }
}
