// SPDX-License-Identifier: GPL-2.0-or-later
package org.printy.app.ui

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.provider.Settings
import android.text.format.DateUtils
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import org.printy.app.data.*
import org.printy.app.BuildConfig
import org.printy.app.printing.*
import org.printy.escp.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun PrintyUi(vm: PrintyViewModel) {
    val printers by vm.printers.collectAsStateWithLifecycle()
    val jobs by vm.jobs.collectAsStateWithLifecycle()
    val doc by vm.document.collectAsStateWithLifecycle()
    val ready by vm.onboarded.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    val importing by vm.importing.collectAsStateWithLifecycle()
    val online by vm.online.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    var adding by rememberSaveable { mutableStateOf(false) }
    var editing by remember { mutableStateOf<PrinterProfile?>(null) }
    var about by rememberSaveable { mutableStateOf(false) }
    var showLicense by rememberSaveable { mutableStateOf(false) }
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current
    LaunchedEffect(lifecycle, printers) {
        lifecycle.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) { vm.refresh(); delay(15_000) }
        }
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let(vm::import) }
    var afterPermission by remember { mutableStateOf<(() -> Unit)?>(null) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { afterPermission?.invoke(); afterPermission = null }
    fun print(action: () -> Unit) {
        if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            afterPermission = action; permission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else action()
    }
    fun enable() { context.startActivity(Intent(Settings.ACTION_PRINT_SETTINGS)) }
    BackHandler(doc != null && ready) { vm.document.value = null }
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(title = { Text(if (doc != null && ready) "Print preview" else "Printy", fontWeight = FontWeight.SemiBold) },
                navigationIcon = { if (doc != null && ready) IconButton(onClick = { vm.document.value = null }) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") } },
                actions = { IconButton(onClick = { about = true }) { Icon(Icons.Outlined.Info, "About Printy") } })
        },
        floatingActionButton = {
            if (ready && doc == null) ExtendedFloatingActionButton(onClick = { adding = true }, icon = { Icon(Icons.Outlined.Add, null) }, text = { Text("Add printer") })
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            Column(Modifier.widthIn(max = 760.dp).fillMaxSize()) {
                if (importing) LinearProgressIndicator(Modifier.fillMaxWidth())
                if (error != null) ErrorCard(error!!, { vm.error.value = null })
                when {
                    !ready -> Onboarding(printers, jobs, onSave = { vm.app.printers.save(it) },
                        onTest = { p, callback -> print { callback(vm.print(p, true)) } }, onDone = vm::finishOnboarding, onEnable = ::enable,
                        onCancel = vm.app.jobs::cancel)
                    doc != null -> PreviewScreen(doc!!, printers, selected, settings,
                        onSelect = { selected = it }, onSettings = { vm.settings.value = it },
                        onPrint = { p -> print { vm.print(p) } }, onAdd = { adding = true }, jobs = jobs,
                        onCancel = vm.app.jobs::cancel, onDismiss = vm.app.jobs::dismiss)
                    else -> HomeScreen(printers, online, jobs, onPick = { picker.launch(arrayOf("application/pdf", "image/*")) },
                        onEnable = ::enable, onAdd = { adding = true }, onEdit = { editing = it },
                        onTest = { p -> print { vm.print(p, true) } }, onCancel = vm.app.jobs::cancel, onDismiss = vm.app.jobs::dismiss)
                }
            }
        }
    }
    if (adding || editing != null) PrinterDialog(editing, onDismiss = { adding = false; editing = null },
        onSave = { vm.app.printers.save(it); selected = it.id; adding = false; editing = null },
        onDelete = { vm.app.printers.remove(it.id); editing = null })
    if (about) AlertDialog(onDismissRequest = { about = false }, icon = { Icon(Icons.Outlined.FavoriteBorder, null) },
        title = { Text("Made for your printer.\nFree for everyone.") },
        text = { Text("Printy ${BuildConfig.VERSION_NAME}\n\nOpen source under GPLv2 or later. No ads, accounts, analytics or watermarks.\n\nPrinter support is based on Gutenprint by Michael Sweet, Robert Krawitz and contributors.\n\nEarly support: Epson L120, L130 and L210. Start with a test page. A network connection cannot confirm paper, ink or jam status.") },
        confirmButton = { TextButton(onClick = { about = false }) { Text("Close") } },
        dismissButton = { TextButton(onClick = { about = false; showLicense = true }) { Text("License & credits") } })
    if (showLicense) {
        val legal = remember { context.assets.open("LICENSE").bufferedReader().use { it.readText() } + "\n\n" + context.assets.open("THIRD_PARTY_NOTICES.md").bufferedReader().use { it.readText() } }
        AlertDialog(onDismissRequest = { showLicense = false }, title = { Text("License & credits") },
            text = { Text(legal, modifier = Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()), style = MaterialTheme.typography.bodySmall) },
            confirmButton = { TextButton(onClick = { showLicense = false }) { Text("Close") } })
    }
}

@Composable private fun HomeScreen(printers: List<PrinterProfile>, online: Map<String, Boolean>, jobs: List<JobState>,
    onPick: () -> Unit, onEnable: () -> Unit, onAdd: () -> Unit, onEdit: (PrinterProfile) -> Unit,
    onTest: (PrinterProfile) -> Unit, onCancel: (String) -> Unit, onDismiss: (String) -> Unit) {
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 12.dp, bottom = 110.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)) {
        item {
            Text("From screen\nto something real.", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(12.dp))
            Text("Your documents and photos, on your printer.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(24.dp))
            Button(onClick = onPick, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                Icon(Icons.Outlined.Description, null); Spacer(Modifier.width(10.dp)); Text("Print a file")
            }
            Text("PDFs & photos", Modifier.fillMaxWidth().padding(top = 8.dp), style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        items(jobs.reversed(), key = { it.id }) { JobCard(it, onCancel, onDismiss) }
        item { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Your printers", style = MaterialTheme.typography.titleLarge)
            Text("${printers.size} saved", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } }
        if (printers.isEmpty()) item {
            OutlinedCard(Modifier.fillMaxWidth()) { Column(Modifier.padding(24.dp)) {
                Icon(Icons.Outlined.Print, null, Modifier.size(36.dp)); Spacer(Modifier.height(12.dp))
                Text("A home for your printer", style = MaterialTheme.typography.titleMedium)
                Text("Add its address once. We'll remember it for next time.", Modifier.padding(vertical = 8.dp))
                TextButton(onClick = onAdd) { Text("Add your first printer") }
            } }
        }
        items(printers, key = { it.id }) { p ->
            ElevatedCard(Modifier.fillMaxWidth(), colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
                            Icon(Icons.Outlined.Print, null, Modifier.padding(16.dp).size(28.dp))
                        }
                        Column(Modifier.weight(1f).padding(horizontal = 16.dp)) {
                            Text(p.name, style = MaterialTheme.typography.titleMedium)
                            Text(p.endpoint, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = { onEdit(p) }) { Icon(Icons.Outlined.Edit, "Edit ${p.name}") }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(when (online[p.id]) { true -> "● Last check: connected"; false -> "○ Last check: unreachable"; null -> "Connection not checked" }, style = MaterialTheme.typography.labelMedium)
                        Text(if (p.lastUsed == 0L) "Not used yet" else "Used ${DateUtils.getRelativeTimeSpanString(p.lastUsed, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS)}",
                            style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    OutlinedButton(onClick = { onTest(p) }, enabled = jobs.none { it.active && it.printer == p.name }) { Text("Test print") }
                }
            }
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                Column(Modifier.fillMaxWidth().padding(20.dp)) {
                    Text("Print from your favorite apps", style = MaterialTheme.typography.titleMedium)
                    Text("Turn on Printy in Android's printing settings. Then choose Print in Chrome, Docs, Photos and more.", Modifier.padding(top = 8.dp))
                    TextButton(onClick = onEnable) { Text("Open printing settings"); Spacer(Modifier.width(8.dp)); Icon(Icons.AutoMirrored.Outlined.ArrowForward, null) }
                }
            }
        }
        item { Text("No ads. No subscriptions. Just printing.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable private fun Onboarding(printers: List<PrinterProfile>, jobs: List<JobState>, onSave: (PrinterProfile) -> Unit,
    onTest: (PrinterProfile, (String?) -> Unit) -> Unit, onDone: () -> Unit, onEnable: () -> Unit, onCancel: (String) -> Unit) {
    var step by rememberSaveable { mutableIntStateOf(if (printers.isEmpty()) 0 else 1) }
    var profileId by rememberSaveable { mutableStateOf(printers.firstOrNull()?.id) }
    var testId by rememberSaveable { mutableStateOf<String?>(null) }
    val profile = printers.find { it.id == profileId } ?: printers.firstOrNull()
    val job = jobs.find { it.id == testId }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { repeat(3) { n ->
            Box(Modifier.weight(1f).height(4.dp).background(if (n <= step) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest, RoundedCornerShape(2.dp)))
        } }
        Text("A little setup. A lot to print.", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
            Icon(if (step == 2) Icons.Outlined.CheckCircle else Icons.Outlined.Print, null, Modifier.padding(24.dp).size(56.dp))
        }
        Text(listOf("Let's meet your printer.", "Let's try a page.", "You're ready to print.")[step], style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.SemiBold)
        when (step) {
            0 -> {
                Text("Enter your printer's IP address. If it's plugged into your router, use the router's address.")
                PrinterFields(profile, "Continue") { p -> onSave(p); profileId = p.id; step = 1 }
            }
            1 -> {
                Text("Put plain A4 paper in ${profile?.name ?: "your printer"}. We'll send one page to make sure everything looks right.")
                if (job != null) JobCard(job, onCancel, {})
                Button(onClick = { profile?.let { onTest(it) { id -> testId = id } } }, enabled = profile != null && job?.active != true, modifier = Modifier.fillMaxWidth().height(54.dp)) { Text(if (job == null) "Send a test print" else "Send another test page") }
                if (job?.phase == JobPhase.SENT) Button(onClick = { step = 2 }, modifier = Modifier.fillMaxWidth()) { Text("My page printed") }
                if (job?.phase == JobPhase.SENT || job?.phase == JobPhase.FAILED) Text("No page? Check the printer's paper and ink, and that USB printer sharing is enabled on your router.", style = MaterialTheme.typography.bodyMedium)
                Row {
                    TextButton(onClick = { step = 0 }) { Text("Change details") }
                    TextButton(onClick = { step = 2 }) { Text("Set up now, test later") }
                }
            }
            else -> {
                Text("Pick a file in Printy, or turn on Printy in Android's printing settings to print from other apps.")
                OutlinedButton(onClick = onEnable, modifier = Modifier.fillMaxWidth()) { Text("Enable printing from other apps") }
                Button(onClick = onDone, modifier = Modifier.fillMaxWidth().height(54.dp)) { Text("Let's print") }
                Text("Free and open source. Always.", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable private fun PrinterFields(original: PrinterProfile?, button: String, onSave: (PrinterProfile) -> Unit) {
    var name by rememberSaveable(original?.id) { mutableStateOf(original?.name ?: "My Epson") }
    var host by rememberSaveable(original?.id) { mutableStateOf(original?.host ?: "") }
    var port by rememberSaveable(original?.id) { mutableStateOf(original?.port?.toString() ?: "9100") }
    var model by rememberSaveable(original?.id) { mutableStateOf(original?.modelId ?: "l130") }
    var advanced by rememberSaveable { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(host, { host = it }, label = { Text("Printer IP address") }, placeholder = { Text("192.168.1.1") }, singleLine = true, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri))
        OutlinedTextField(name, { name = it }, label = { Text("Printer name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Choice("Printer model", EpsonModel.supported.map { it.id to it.label }, model) { model = it }
        TextButton(onClick = { advanced = !advanced }) { Text(if (advanced) "Hide connection details" else "Connection details") }
        if (advanced) OutlinedTextField(port, { port = it }, label = { Text("Port") }, supportingText = { Text("Usually 9100. Change only if your router uses a different port.") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
        Text("Early printer support. Start with a test page.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (error != null) Text(error!!, color = MaterialTheme.colorScheme.error)
        Button(onClick = {
            error = ProfileValidation.error(name, host, port)
            if (error == null) onSave(PrinterProfile(original?.id ?: java.util.UUID.randomUUID().toString(), name.trim(), host.trim(), port.toInt(), model, original?.lastUsed ?: 0))
        }, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text(button) }
    }
}

@Composable private fun PrinterDialog(original: PrinterProfile?, onDismiss: () -> Unit, onSave: (PrinterProfile) -> Unit, onDelete: (PrinterProfile) -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text(if (original == null) "Add a printer" else "Printer details") },
        text = { Column(Modifier.verticalScroll(rememberScrollState())) { PrinterFields(original, "Save printer", onSave) } },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
        dismissButton = { if (original != null) TextButton(onClick = { onDelete(original) }) { Text("Remove", color = MaterialTheme.colorScheme.error) } })
}

@Composable private fun PreviewScreen(doc: LocalDocument, printers: List<PrinterProfile>, selected: String?, settings: PrintSettings,
    onSelect: (String) -> Unit, onSettings: (PrintSettings) -> Unit, onPrint: (PrinterProfile) -> Unit, onAdd: () -> Unit,
    jobs: List<JobState>, onCancel: (String) -> Unit, onDismiss: (String) -> Unit) {
    var page by rememberSaveable(doc.file.path, settings.selection, settings.pageRange, settings.layout, settings.reverse) { mutableIntStateOf(0) }
    val planResult = remember(doc.pages, settings.selection, settings.pageRange, settings.layout, settings.reverse) {
        runCatching { PrintPlan.create(doc.pages, settings) }
    }
    val plan = planResult.getOrNull()
    val selectionError = planResult.exceptionOrNull()?.message
    val sheet = plan?.sheets?.getOrNull(page)
    val previewSettings = settings.copy(copies = 1)
    // Key the image to its settings so an old preview can never enable a newly changed print.
    var bitmap by remember(doc, sheet, previewSettings) { mutableStateOf<Bitmap?>(null) }
    var previewError by remember(doc, sheet, previewSettings) { mutableStateOf<String?>(null) }
    val printer = printers.find { it.id == selected } ?: printers.firstOrNull()
    LaunchedEffect(doc, sheet, previewSettings) {
        if (sheet == null) return@LaunchedEffect
        try { bitmap = Documents.preview(doc, sheet, previewSettings) }
        catch (e: CancellationException) { throw e }
        catch (e: Exception) { previewError = PrintErrors.message(e) }
        catch (e: OutOfMemoryError) { previewError = PrintErrors.message(e) }
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        item {
            Text(doc.name, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(16.dp))
            Surface(Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surfaceContainer, shape = RoundedCornerShape(24.dp)) {
                Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.fillMaxWidth().heightIn(min = 160.dp, max = 400.dp), contentAlignment = Alignment.Center) {
                        if (bitmap != null) Image(bitmap!!.asImageBitmap(), "Sheet ${page + 1}: ${sheet?.description}", Modifier.fillMaxWidth().heightIn(max = 400.dp))
                        else if (selectionError != null) Text(selectionError, Modifier.padding(16.dp), color = MaterialTheme.colorScheme.error)
                        else if (previewError != null) Text(previewError!!, Modifier.padding(16.dp), color = MaterialTheme.colorScheme.error)
                        else CircularProgressIndicator()
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { page-- }, enabled = plan != null && page > 0) { Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, "Previous sheet") }
                        Text(if (plan == null) "Choose pages to preview" else "Sheet ${page + 1} of ${plan.sheets.size}", style = MaterialTheme.typography.labelLarge)
                        IconButton(onClick = { page++ }, enabled = plan != null && page < plan.sheets.lastIndex) { Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, "Next sheet") }
                    }
                    sheet?.let { Text(it.description, style = MaterialTheme.typography.bodySmall) }
                }
            }
        }
        item {
            if (printer == null) OutlinedButton(onClick = onAdd, modifier = Modifier.fillMaxWidth()) { Text("Add a printer to continue") }
            else Choice("Send to", printers.map { it.id to it.name }, printer.id, onSelect)
        }
        item {
            OutlinedCard { Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Choice("Pages to print", PageSelection.entries.map { it.name to it.label }, settings.selection.name) {
                    onSettings(settings.copy(selection = PageSelection.valueOf(it)))
                }
                if (settings.selection == PageSelection.CUSTOM) {
                    OutlinedTextField(settings.pageRange, { onSettings(settings.copy(pageRange = it.take(2000))) },
                        label = { Text("Page numbers") }, placeholder = { Text("1, 3–5") }, singleLine = true,
                        isError = selectionError != null, modifier = Modifier.fillMaxWidth(),
                        supportingText = { Text(selectionError ?: "${doc.pages} pages in this document. Separate pages with commas.") })
                }
                Choice("Layout", SheetLayout.entries.map { it.name to it.label }, settings.layout.name) {
                    val layout = SheetLayout.valueOf(it)
                    onSettings(settings.copy(layout = layout, landscape = if (layout == SheetLayout.TWO) true else settings.landscape))
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Reverse page order", style = MaterialTheme.typography.titleSmall)
                        Text("Print the last selected page first", style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(settings.reverse, { onSettings(settings.copy(reverse = it)) })
                }
                HorizontalDivider()
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("Copies", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                    IconButton(onClick = { onSettings(settings.copy(copies = settings.copies - 1)) }, enabled = settings.copies > 1) { Icon(Icons.Outlined.Remove, "Fewer copies") }
                    Text(settings.copies.toString(), style = MaterialTheme.typography.titleMedium)
                    IconButton(onClick = { onSettings(settings.copy(copies = settings.copies + 1)) }, enabled = settings.copies < 99) { Icon(Icons.Outlined.Add, "More copies") }
                }
                HorizontalDivider()
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) { Text("Print in color", style = MaterialTheme.typography.titleSmall); Text(if (settings.grayscale) "Black & white" else "Full color", style = MaterialTheme.typography.bodySmall) }
                    Switch(!settings.grayscale, { onSettings(settings.copy(grayscale = !it)) })
                }
                Choice("Paper size", Paper.entries.map { it.name to it.label }, settings.paper.name) { onSettings(settings.copy(paper = Paper.valueOf(it))) }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    FilterChip(!settings.landscape, { onSettings(settings.copy(landscape = false)) }, label = { Text("Portrait") })
                    FilterChip(settings.landscape, { onSettings(settings.copy(landscape = true)) }, label = { Text("Landscape") })
                }
                Text("A small white margin keeps everything on the page.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (settings.layout != SheetLayout.ONE)
                    Text("Pages go across, then down. Any unused space on the last sheet stays blank.", style = MaterialTheme.typography.bodySmall)
            } }
        }
        item {
            val total = (plan?.sheets?.size ?: 0) * settings.copies
            plan?.let { Text("${it.selectedPages.size} selected ${if (it.selectedPages.size == 1) "page" else "pages"} · ${settings.copies} ${if (settings.copies == 1) "copy" else "copies"}",
                style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(bottom = 8.dp)) }
            Button(onClick = { printer?.let(onPrint) }, enabled = printer != null && plan != null && bitmap != null && jobs.none { it.active && it.title == doc.name }, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                Icon(Icons.Outlined.Print, null); Spacer(Modifier.width(10.dp)); Text(if (plan == null) "Choose pages to print" else "Print $total ${if (total == 1) "sheet" else "sheets"}")
            }
        }
        items(jobs.reversed(), key = { it.id }) { JobCard(it, onCancel, onDismiss) }
    }
}

@Composable private fun Choice(label: String, options: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column { Text(label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(bottom = 4.dp))
        Box {
            OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), contentPadding = PaddingValues(16.dp)) {
                Text(options.find { it.first == selected }?.second ?: "Choose…", Modifier.weight(1f)); Icon(Icons.Outlined.KeyboardArrowDown, null)
            }
            DropdownMenu(expanded, { expanded = false }) { options.forEach { (key, title) ->
                DropdownMenuItem(text = { Text(title) }, onClick = { onSelect(key); expanded = false })
            } }
        }
    }
}

@Composable private fun JobCard(job: JobState, onCancel: (String) -> Unit, onDismiss: (String) -> Unit) {
    val context = LocalContext.current
    var hidden by rememberSaveable(job.id, job.phase) { mutableStateOf(false) }
    var copied by rememberSaveable(job.id) { mutableStateOf(false) }
    if (hidden) return
    Card(colors = CardDefaults.cardColors(containerColor = if (job.phase == JobPhase.FAILED) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.tertiaryContainer), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(job.title, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                IconButton(onClick = { hidden = true; if (!job.active) onDismiss(job.id) }) { Icon(Icons.Outlined.Close, "Dismiss progress") }
            }
            Text(job.message, style = MaterialTheme.typography.bodyMedium)
            if (job.active) {
                if (job.total == 0) LinearProgressIndicator(Modifier.fillMaxWidth()) else LinearProgressIndicator(progress = { job.progress }, modifier = Modifier.fillMaxWidth())
                TextButton(onClick = { onCancel(job.id) }) { Text("Cancel print") }
            }
            if (job.details.isNotEmpty()) TextButton(onClick = {
                context.getSystemService(ClipboardManager::class.java)
                    .setPrimaryClip(ClipData.newPlainText("Printy job details", job.details))
                copied = true
            }) { Text(if (copied) "Details copied" else "Copy job details") }
        }
    }
}

@Composable private fun ErrorCard(message: String, onDismiss: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.errorContainer, modifier = Modifier.fillMaxWidth().padding(16.dp), shape = RoundedCornerShape(16.dp)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(message, Modifier.weight(1f)); IconButton(onClick = onDismiss) { Icon(Icons.Outlined.Close, "Dismiss error") }
        }
    }
}
