package com.example.workpunch

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.snapping.SnapLayoutInfoProvider
import androidx.compose.foundation.gestures.snapping.snapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.workpunch.data.PunchRecord
import com.example.workpunch.data.PunchRepository
import com.example.workpunch.data.StatsSummary
import com.example.workpunch.data.chineseStatutoryHoliday
import com.example.workpunch.data.minutesToCalendarText
import com.example.workpunch.data.minutesToCalendarDecimalText
import com.example.workpunch.data.minutesToDecimalHourText
import com.example.workpunch.data.minutesToHourText
import com.example.workpunch.data.toLocalTimeText
import com.example.workpunch.data.weekStart
import com.example.workpunch.widget.PunchWidgetProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.abs

class MainActivity : ComponentActivity() {
    private val viewModel by viewModels<PunchViewModel> {
        PunchViewModel.Factory((application as WorkPunchApplication).repository, applicationContext)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            WorkPunchTheme {
                WorkPunchApp(viewModel)
            }
        }
    }
}

enum class AppTab(val label: String, val icon: ImageVector) {
    Today("今日", Icons.Default.Schedule),
    Calendar("日历", Icons.Default.CalendarMonth),
    Stats("统计", Icons.Default.BarChart)
}

enum class StatsMode(val label: String) {
    Week("本周"),
    Month("本月"),
    Year("本年"),
    Custom("自选")
}

enum class TimeDisplayMode(val label: String) {
    HourMinute("时分"),
    Decimal("十进制")
}

private enum class DateRangeTarget {
    Start,
    End
}

private enum class CalendarTransitionDirection {
    Previous,
    Next
}

data class StatsRange(
    val start: LocalDate,
    val end: LocalDate,
    val mode: StatsMode
)

private val SheetShape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
private val DialogShape = RoundedCornerShape(24.dp)
private val ControlShape = RoundedCornerShape(12.dp)
private val SelectionShape = RoundedCornerShape(10.dp)
private val SheetContentHeight = 560.dp
private val MonthSheetContentHeight = 430.dp
private val StandardWheelItemHeight = 46.dp

@OptIn(ExperimentalCoroutinesApi::class)
class PunchViewModel(
    private val repository: PunchRepository,
    private val appContext: Context
) : ViewModel() {
    private val selectedMonth = MutableStateFlow(LocalDate.now().withDayOfMonth(1))
    private val selectedTab = MutableStateFlow(AppTab.Today)
    private val statsRange = MutableStateFlow(defaultStatsRange(StatsMode.Week))
    private val displayPreferences = appContext.getSharedPreferences("display_preferences", Context.MODE_PRIVATE)
    private val selectedTimeDisplay = MutableStateFlow(
        displayPreferences.getString("time_display_mode", null)
            ?.let { stored -> TimeDisplayMode.entries.firstOrNull { it.name == stored } }
            ?: TimeDisplayMode.HourMinute
    )

    val tab = selectedTab
    val month = selectedMonth
    val range = statsRange
    val timeDisplayMode = selectedTimeDisplay

    val today = repository.observeToday()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val monthRecords = selectedMonth.flatMapLatest { repository.observeMonth(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val stats = statsRange.flatMapLatest { repository.observeStats(it.start, it.end) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StatsSummary(0, 0, 0, 0, 0, 0))

    fun selectTab(tab: AppTab) {
        selectedTab.value = tab
    }

    fun previousMonth() {
        selectedMonth.value = selectedMonth.value.minusMonths(1)
    }

    fun nextMonth() {
        selectedMonth.value = selectedMonth.value.plusMonths(1)
    }

    fun setMonth(month: LocalDate) {
        selectedMonth.value = month.withDayOfMonth(1)
    }

    fun setStatsMode(mode: StatsMode) {
        statsRange.value = defaultStatsRange(mode)
    }

    fun setStatsRange(start: LocalDate, end: LocalDate) {
        statsRange.value = StatsRange(minOf(start, end), maxOf(start, end), StatsMode.Custom)
    }

    fun setTimeDisplayMode(mode: TimeDisplayMode) {
        selectedTimeDisplay.value = mode
        displayPreferences.edit().putString("time_display_mode", mode.name).apply()
    }

    fun clockIn() = viewModelScope.launch {
        repository.clockIn()
        PunchWidgetProvider.refreshAllWidgets(appContext)
    }

    fun clockOut() = viewModelScope.launch {
        repository.clockOut()
        PunchWidgetProvider.refreshAllWidgets(appContext)
    }

    fun saveRecord(date: LocalDate, clockInMillis: Long?, clockOutMillis: Long?) = viewModelScope.launch {
        repository.saveRecord(date, clockInMillis, clockOutMillis)
        if (date == LocalDate.now()) PunchWidgetProvider.refreshAllWidgets(appContext)
    }

    fun deleteRecord(date: LocalDate) = viewModelScope.launch {
        repository.deleteRecord(date)
        if (date == LocalDate.now()) PunchWidgetProvider.refreshAllWidgets(appContext)
    }

    class Factory(
        private val repository: PunchRepository,
        private val appContext: Context
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return PunchViewModel(repository, appContext) as T
        }
    }
}

private fun defaultStatsRange(mode: StatsMode): StatsRange {
    val today = LocalDate.now()
    return when (mode) {
        StatsMode.Week -> StatsRange(today.weekStart(), today.weekStart().plusDays(6), mode)
        StatsMode.Month -> StatsRange(today.withDayOfMonth(1), today.withDayOfMonth(today.lengthOfMonth()), mode)
        StatsMode.Year -> StatsRange(today.withDayOfYear(1), today.withDayOfYear(today.lengthOfYear()), mode)
        StatsMode.Custom -> StatsRange(today.minusDays(6), today, mode)
    }
}

@Composable
fun WorkPunchApp(viewModel: PunchViewModel) {
    val tab by viewModel.tab.collectAsState()
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                AppTab.entries.forEach { item ->
                    NavigationBarItem(
                        selected = tab == item,
                        onClick = { viewModel.selectTab(item) },
                        icon = { Icon(item.icon, contentDescription = item.label) },
                        label = { Text(item.label) }
                    )
                }
            }
        }
    ) { padding ->
        Surface(
            color = MaterialTheme.colorScheme.background,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when (tab) {
                AppTab.Today -> TodayScreen(viewModel)
                AppTab.Calendar -> CalendarScreen(viewModel)
                AppTab.Stats -> StatsScreen(viewModel)
            }
        }
    }
}

@Composable
private fun TodayScreen(viewModel: PunchViewModel) {
    val record by viewModel.today.collectAsState()
    val timeDisplayMode by viewModel.timeDisplayMode.collectAsState()
    val nowMillis = currentTimeMillis()
    val workedMinutes = record?.workedMinutesAt(nowMillis)
    val isWorking = record?.clockInMillis != null && record?.clockOutMillis == null
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text("今天", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
            Text(todayText(), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item {
            InfoCard(title = "上班时间", value = record?.clockInMillis?.toLocalTimeText() ?: "未打卡")
        }
        item {
            InfoCard(title = "下班时间", value = record?.clockOutMillis?.toLocalTimeText() ?: "未打卡")
        }
        item {
            InfoCard(
                title = if (isWorking) "今日已工作" else "今日工时",
                value = workedMinutes?.let { formatWorkTime(it, timeDisplayMode) } ?: "尚未上班"
            )
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                PunchButton(
                    "上班",
                    onClick = viewModel::clockIn,
                    enabled = record?.clockInMillis == null,
                    modifier = Modifier.weight(1f)
                )
                PunchButton(
                    "下班",
                    onClick = viewModel::clockOut,
                    enabled = record?.clockOutMillis == null,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CalendarScreen(viewModel: PunchViewModel) {
    val month by viewModel.month.collectAsState()
    val records by viewModel.monthRecords.collectAsState()
    var selectedDate by remember { mutableStateOf<LocalDate?>(null) }
    var editingDate by remember { mutableStateOf<LocalDate?>(null) }
    var deletingDate by remember { mutableStateOf<LocalDate?>(null) }
    var showMonthPicker by remember { mutableStateOf(false) }
    var horizontalDragDistance by remember { mutableFloatStateOf(0f) }
    var transitionDirection by remember { mutableStateOf(CalendarTransitionDirection.Next) }
    val nowMillis = currentTimeMillis()
    val recordMap = records.associateBy { it.epochDay }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp)
            .pointerInput(month) {
                detectHorizontalDragGestures(
                    onHorizontalDrag = { _, dragAmount ->
                        horizontalDragDistance += dragAmount
                    },
                    onDragCancel = { horizontalDragDistance = 0f },
                    onDragEnd = {
                        when {
                            horizontalDragDistance <= -72f -> {
                                transitionDirection = CalendarTransitionDirection.Next
                                viewModel.nextMonth()
                            }
                            horizontalDragDistance >= 72f -> {
                                transitionDirection = CalendarTransitionDirection.Previous
                                viewModel.previousMonth()
                            }
                        }
                        horizontalDragDistance = 0f
                    }
                )
        },
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        AnimatedContent(
            targetState = month,
            modifier = Modifier.weight(1f),
            transitionSpec = {
                val forward = transitionDirection == CalendarTransitionDirection.Next
                (slideInHorizontally(animationSpec = tween(220)) { width -> if (forward) width else -width } + fadeIn()) togetherWith
                    (slideOutHorizontally(animationSpec = tween(220)) { width -> if (forward) -width else width } + fadeOut())
            },
            label = "日历切月"
        ) { displayedMonth ->
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    IconButton(onClick = {
                        transitionDirection = CalendarTransitionDirection.Previous
                        viewModel.previousMonth()
                    }) {
                        Icon(Icons.Default.KeyboardArrowLeft, contentDescription = "上个月")
                    }
                    TextButton(onClick = { showMonthPicker = true }, modifier = Modifier.weight(1f)) {
                        Text(
                            displayedMonth.format(DateTimeFormatter.ofPattern("yyyy年M月")),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    IconButton(onClick = {
                        transitionDirection = CalendarTransitionDirection.Next
                        viewModel.nextMonth()
                    }) {
                        Icon(Icons.Default.KeyboardArrowRight, contentDescription = "下个月")
                    }
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    listOf("一", "二", "三", "四", "五", "六", "日").forEach {
                        Text(it, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                LazyVerticalGrid(
                    columns = GridCells.Fixed(7),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    items(buildMonthCells(displayedMonth)) { date ->
                        DayCell(
                            date = date,
                            record = date?.let { recordMap[it.toEpochDay()] },
                            nowMillis = nowMillis,
                            onClick = date?.let { { selectedDate = it } }
                        )
                    }
                }
            }
        }
    }
    if (showMonthPicker) {
        MonthPickerBottomSheet(
            initialMonth = month,
            onDismiss = { showMonthPicker = false },
            onConfirm = {
                viewModel.setMonth(it)
                showMonthPicker = false
            }
        )
    }
    selectedDate?.let { date ->
        DayDetailDialog(
            date = date,
            record = recordMap[date.toEpochDay()],
            onDismiss = { selectedDate = null },
            onEdit = {
                selectedDate = null
                editingDate = date
            },
            onDelete = {
                selectedDate = null
                deletingDate = date
            }
        )
    }
    editingDate?.let { date ->
        PunchEditorDialog(
            date = date,
            record = recordMap[date.toEpochDay()],
            onDismiss = { editingDate = null },
            onSave = { clockInMillis, clockOutMillis ->
                viewModel.saveRecord(date, clockInMillis, clockOutMillis)
                editingDate = null
            }
        )
    }
    deletingDate?.let { date ->
        DeleteRecordDialog(
            date = date,
            onDismiss = { deletingDate = null },
            onConfirm = {
                viewModel.deleteRecord(date)
                deletingDate = null
            }
        )
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun MonthPickerBottomSheet(
    initialMonth: LocalDate,
    onDismiss: () -> Unit,
    onConfirm: (LocalDate) -> Unit
) {
    var pendingMonth by remember(initialMonth) { mutableStateOf(initialMonth) }
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { it != SheetValue.Hidden }
    )
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = SheetShape,
        dragHandle = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(MonthSheetContentHeight)
                .padding(horizontal = 20.dp)
        ) {
            SheetHeader(title = "选择年月", onClose = onDismiss)
            Spacer(Modifier.height(16.dp))
            WheelYearMonthPicker(
                selectedMonth = pendingMonth,
                onMonthSelected = { pendingMonth = it }
            )
            Spacer(Modifier.weight(1f))
            SheetActionRow(
                onCancel = onDismiss,
                onConfirm = { onConfirm(pendingMonth) },
                modifier = Modifier.padding(bottom = 20.dp)
            )
        }
    }
}

@Composable
private fun SheetHeader(
    title: String,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            title,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        IconButton(
            onClick = onClose,
            modifier = Modifier.size(40.dp)
        ) {
            Icon(
                Icons.Default.Close,
                contentDescription = "关闭",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SheetActionRow(
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
    confirmEnabled: Boolean = true
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        OutlinedButton(
            onClick = onCancel,
            shape = ControlShape,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier
                .weight(1f)
                .height(48.dp)
        ) {
            Text(
                "取消",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
        }
        Button(
            onClick = onConfirm,
            enabled = confirmEnabled,
            shape = ControlShape,
            modifier = Modifier
                .weight(1f)
                .height(48.dp)
        ) {
            Text(
                "确定",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun WheelYearMonthPicker(
    selectedMonth: LocalDate,
    onMonthSelected: (LocalDate) -> Unit,
    modifier: Modifier = Modifier
) {
    ConnectedWheelPickerRow(modifier = modifier) {
        WheelNumberPicker(
            value = selectedMonth.year,
            range = 2000..2100,
            onValueChange = { year -> onMonthSelected(LocalDate.of(year, selectedMonth.monthValue, 1)) },
            modifier = Modifier.weight(1f),
            showSelectionBackground = false,
            formatter = { value -> "${value}年" }
        )
        WheelNumberPicker(
            value = selectedMonth.monthValue,
            range = 1..12,
            onValueChange = { value -> onMonthSelected(LocalDate.of(selectedMonth.year, value, 1)) },
            modifier = Modifier.weight(1f),
            showSelectionBackground = false,
            formatter = { value -> "${value}月" }
        )
    }
}

@Composable
private fun ConnectedWheelPickerRow(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(StandardWheelItemHeight * 5),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(StandardWheelItemHeight)
                .background(MaterialTheme.colorScheme.surfaceVariant)
        )
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
            content = content
        )
    }
}

private fun LazyListState.centeredItemIndex(): Int? {
    val layoutInfo = layoutInfo
    val viewportCenter = (layoutInfo.viewportStartOffset + layoutInfo.viewportEndOffset) / 2
    return layoutInfo.visibleItemsInfo.minByOrNull { item ->
        abs((item.offset + item.size / 2) - viewportCenter)
    }?.index
}

@Composable
private fun WheelNumberPicker(
    value: Int,
    range: IntRange,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    showSelectionBackground: Boolean = true,
    formatter: (Int) -> String = { it.toString() }
) {
    val values = remember(range) { range.toList() }
    val itemHeight = if (compact) 36.dp else StandardWheelItemHeight
    val visibleItems = if (compact) 3 else 5
    val selectedIndex = values.indexOf(value).coerceAtLeast(0)
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = selectedIndex)
    val flingBehavior = remember(listState) {
        snapFlingBehavior(
            snapLayoutInfoProvider = SnapLayoutInfoProvider(listState),
            decayAnimationSpec = exponentialDecay<Float>(frictionMultiplier = 1.7f),
            snapAnimationSpec = spring(stiffness = Spring.StiffnessMediumLow)
        )
    }
    var visualIndex by remember(values) { mutableIntStateOf(selectedIndex) }
    LaunchedEffect(selectedIndex) {
        if (!listState.isScrollInProgress && listState.centeredItemIndex() != selectedIndex) {
            listState.animateScrollToItem(selectedIndex)
        }
        visualIndex = selectedIndex
    }
    LaunchedEffect(listState, values) {
        snapshotFlow { listState.centeredItemIndex() }
            .distinctUntilChanged()
            .collect { index -> index?.let { visualIndex = it } }
    }
    LaunchedEffect(listState, values, visualIndex) {
        snapshotFlow { listState.isScrollInProgress }
            .distinctUntilChanged()
            .collect { isScrolling ->
                if (!isScrolling) values.getOrNull(visualIndex)?.let(onValueChange)
            }
    }
    Box(
        modifier = modifier.height(itemHeight * visibleItems),
        contentAlignment = Alignment.Center
    ) {
        if (showSelectionBackground) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(itemHeight)
                    .background(MaterialTheme.colorScheme.surfaceVariant, SelectionShape)
            )
        }
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(vertical = itemHeight * ((visibleItems - 1) / 2)),
            flingBehavior = flingBehavior,
            modifier = Modifier.fillMaxSize()
        ) {
            itemsIndexed(values) { index, number ->
                val distance = abs(index - visualIndex)
                val alpha = when (distance) {
                    0 -> 1f
                    1 -> 0.38f
                    else -> 0.14f
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(itemHeight)
                        .clickable { onValueChange(number) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        formatter(number),
                        style = if (compact) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha),
                        fontWeight = if (distance == 0) FontWeight.Medium else FontWeight.Normal
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StatsScreen(viewModel: PunchViewModel) {
    val range by viewModel.range.collectAsState()
    val stats by viewModel.stats.collectAsState()
    val timeDisplayMode by viewModel.timeDisplayMode.collectAsState()
    var showRangePicker by remember { mutableStateOf(false) }
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("工时统计", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                TimeDisplayToggle(
                    selected = timeDisplayMode,
                    onSelected = viewModel::setTimeDisplayMode
                )
            }
        }
        item { StatsModeSelector(selectedMode = range.mode, onSelected = viewModel::setStatsMode) }
        item {
            Button(onClick = { showRangePicker = true }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.CalendarMonth, contentDescription = null)
                Spacer(Modifier.padding(4.dp))
                Text(
                    formatDateRange(range.start, range.end),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        item {
            InfoCard(
                title = if (range.mode == StatsMode.Year) "年均工时" else "日均工时",
                value = formatWorkTime(stats.averageMinutes, timeDisplayMode)
            )
        }
        item {
            InfoCard(
                title = "周均工时",
                value = formatWorkTime(stats.weeklyAverageMinutes, timeDisplayMode)
            )
        }
        item {
            InfoCard(
                title = "总工时",
                value = formatWorkTime(stats.totalMinutes, timeDisplayMode)
            )
        }
        item {
            InfoCard(title = "上班天数", value = "${stats.clockInDays}天")
        }
    }
    if (showRangePicker) {
        DateRangeBottomSheet(
            initialStart = range.start,
            initialEnd = range.end,
            onDismiss = { showRangePicker = false },
            onConfirm = { start, end ->
                viewModel.setStatsRange(start, end)
                showRangePicker = false
            }
        )
    }
}

@Composable
private fun StatsModeSelector(
    selectedMode: StatsMode,
    onSelected: (StatsMode) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(40.dp)
            .clip(ControlShape)
            .background(MaterialTheme.colorScheme.surface)
            .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f), ControlShape)
    ) {
        StatsMode.entries.forEachIndexed { index, mode ->
            val selected = selectedMode == mode
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                    .clickable { onSelected(mode) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    mode.label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                )
            }
            if (index < StatsMode.entries.lastIndex) {
                Box(
                    modifier = Modifier
                        .width(0.5.dp)
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
                )
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun DateRangeBottomSheet(
    initialStart: LocalDate,
    initialEnd: LocalDate?,
    onDismiss: () -> Unit,
    onConfirm: (LocalDate, LocalDate) -> Unit
) {
    var startDate by remember(initialStart) { mutableStateOf(initialStart) }
    var endDate by remember(initialEnd) { mutableStateOf(initialEnd) }
    var activeTarget by remember { mutableStateOf(DateRangeTarget.Start) }
    val pickerDate = if (activeTarget == DateRangeTarget.Start) startDate else endDate ?: startDate
    val canConfirm = endDate != null && !endDate!!.isBefore(startDate)
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { it != SheetValue.Hidden }
    )

    fun updateActiveDate(date: LocalDate) {
        when (activeTarget) {
            DateRangeTarget.Start -> {
                startDate = date
                if (endDate != null && endDate!!.isBefore(date)) {
                    endDate = null
                }
            }
            DateRangeTarget.End -> endDate = date
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = SheetShape,
        dragHandle = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(SheetContentHeight)
                .padding(horizontal = 20.dp)
        ) {
            SheetHeader(title = "选择时间段", onClose = onDismiss)
            Spacer(Modifier.height(20.dp))
            DateRangeTargetRow(
                startDate = startDate,
                endDate = endDate,
                activeTarget = activeTarget,
                onTargetSelected = { activeTarget = it }
            )
            Spacer(Modifier.height(24.dp))
            DateWheelPicker(
                date = pickerDate,
                onDateChange = ::updateActiveDate
            )
            Spacer(Modifier.weight(1f))
            SheetActionRow(
                onCancel = onDismiss,
                onConfirm = { endDate?.let { onConfirm(startDate, it) } },
                confirmEnabled = canConfirm,
                modifier = Modifier.padding(bottom = 20.dp)
            )
        }
    }
}

@Composable
private fun DateRangeTargetRow(
    startDate: LocalDate,
    endDate: LocalDate?,
    activeTarget: DateRangeTarget,
    onTargetSelected: (DateRangeTarget) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        DateRangeDateButton(
            label = "开始日期",
            date = startDate,
            selected = activeTarget == DateRangeTarget.Start,
            onClick = { onTargetSelected(DateRangeTarget.Start) },
            modifier = Modifier.weight(1f)
        )
        Text(
            "至",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 15.dp)
        )
        DateRangeDateButton(
            label = "结束日期",
            date = endDate,
            selected = activeTarget == DateRangeTarget.End,
            onClick = { onTargetSelected(DateRangeTarget.End) },
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun DateRangeDateButton(
    label: String,
    date: LocalDate?,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))
        Surface(
            onClick = onClick,
            shape = ControlShape,
            color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
            border = if (selected) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    date?.format(DateTimeFormatter.ofPattern("yyyy年M月d日")) ?: "轻触选择日期",
                    style = MaterialTheme.typography.titleSmall,
                    color = when {
                        selected -> MaterialTheme.colorScheme.primary
                        date == null -> MaterialTheme.colorScheme.onSurfaceVariant
                        else -> MaterialTheme.colorScheme.onSurface
                    },
                    fontWeight = if (date == null) FontWeight.Normal else FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun DateWheelPicker(date: LocalDate, onDateChange: (LocalDate) -> Unit) {
    ConnectedWheelPickerRow {
        WheelNumberPicker(
            value = date.year,
            range = 2000..2100,
            onValueChange = { year ->
                onDateChange(date.withYearKeepingDay(year))
            },
            modifier = Modifier.weight(1f),
            showSelectionBackground = false,
            formatter = { "${it}年" }
        )
        WheelNumberPicker(
            value = date.monthValue,
            range = 1..12,
            onValueChange = { month ->
                onDateChange(date.withMonthKeepingDay(month))
            },
            modifier = Modifier.weight(1f),
            showSelectionBackground = false,
            formatter = { "${it}月" }
        )
        WheelNumberPicker(
            value = date.dayOfMonth,
            range = 1..date.lengthOfMonth(),
            onValueChange = { day -> onDateChange(date.withDayOfMonth(day)) },
            modifier = Modifier.weight(1f),
            showSelectionBackground = false,
            formatter = { "${it}日" }
        )
    }
}

@Composable
private fun TimeDisplayToggle(
    selected: TimeDisplayMode,
    onSelected: (TimeDisplayMode) -> Unit
) {
    val isDecimal = selected == TimeDisplayMode.Decimal
    val thumbOffset by animateDpAsState(
        targetValue = if (isDecimal) 2.dp else 54.dp,
        label = "时间显示切换"
    )
    Box(
        modifier = Modifier
            .width(84.dp)
            .height(32.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFFE2E9E4))
            .border(0.5.dp, Color(0xFFC8D3CB), RoundedCornerShape(16.dp))
            .clickable {
                onSelected(
                    if (isDecimal) TimeDisplayMode.HourMinute else TimeDisplayMode.Decimal
                )
            }
    ) {
        Text(
            text = if (isDecimal) "十进制" else "时分",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Medium,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .offset(x = if (isDecimal) 36.dp else 16.dp)
        )
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .offset(x = thumbOffset)
                .size(28.dp)
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(14.dp))
                .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(14.dp))
        )
    }
}

private fun formatWorkTime(minutes: Long, mode: TimeDisplayMode): String {
    return when (mode) {
        TimeDisplayMode.HourMinute -> minutesToHourText(minutes)
        TimeDisplayMode.Decimal -> minutesToDecimalHourText(minutes)
    }
}

@Composable
private fun InfoCard(title: String, value: String) {
    Card(
        shape = ControlShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun PunchButton(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    colors: androidx.compose.material3.ButtonColors = ButtonDefaults.buttonColors()
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        colors = colors,
        shape = ControlShape,
        modifier = modifier.height(52.dp)
    ) {
        Text(text, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun DayCell(date: LocalDate?, record: PunchRecord?, nowMillis: Long, onClick: (() -> Unit)?) {
    val complete = record?.workedMinutes() != null
    val holiday = date?.let(::chineseStatutoryHoliday)
    val displayedMinutes = when {
        complete -> record?.workedMinutes()
        date == LocalDate.now() -> record?.workedMinutesAt(nowMillis)
        else -> null
    }
    val bg = when {
        date == null -> Color.Transparent
        complete -> MaterialTheme.colorScheme.primaryContainer
        record != null -> MaterialTheme.colorScheme.secondaryContainer
        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
    }
    Column(
        modifier = Modifier
            .aspectRatio(0.68f)
            .background(bg, RoundedCornerShape(10.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(6.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(date?.dayOfMonth?.toString().orEmpty(), fontWeight = FontWeight.SemiBold)
        holiday?.let {
            Text(
                it,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
                maxLines = 1
            )
        }
        if (record != null) {
            Text(
                displayedMinutes?.let(::minutesToCalendarDecimalText)
                    ?: listOfNotNull(
                        record.clockInMillis?.let { "上 ${it.toLocalTimeText()}" },
                        record.clockOutMillis?.let { "下 ${it.toLocalTimeText()}" }
                    ).joinToString("\n"),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = if (complete) FontWeight.SemiBold else FontWeight.Normal
            )
        } else if (holiday == null) {
            Spacer(Modifier.height(1.dp))
        }
    }
}

@Composable
private fun DayDetailDialog(
    date: LocalDate,
    record: PunchRecord?,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val holiday = chineseStatutoryHoliday(date)
    val nowMillis = currentTimeMillis()
    val isWorking = date == LocalDate.now() && record?.clockInMillis != null && record.clockOutMillis == null
    val workedMinutes = if (date == LocalDate.now()) record?.workedMinutesAt(nowMillis) else record?.workedMinutes()
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = DialogShape,
        title = {
            Text(
                date.format(DateTimeFormatter.ofPattern("yyyy年M月d日")),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                holiday?.let { Text(it, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.SemiBold) }
                Text("上班：${record?.clockInMillis?.toLocalTimeText() ?: "未打卡"}")
                Text("下班：${record?.clockOutMillis?.toLocalTimeText() ?: "未打卡"}")
                Text(
                    "${if (isWorking) "已工作" else "工时"}：" +
                        (workedMinutes?.let(::minutesToHourText) ?: "等待完整记录") +
                        if (isWorking) "（进行中）" else ""
                )
            }
        },
        confirmButton = {
            Row {
                TextButton(onClick = onEdit) { Text(if (record == null) "添加工时" else "编辑") }
                TextButton(onClick = onDismiss) { Text("关闭") }
            }
        },
        dismissButton = {
            if (record != null) {
                TextButton(onClick = onDelete) { Text("删除记录", color = MaterialTheme.colorScheme.error) }
            }
        }
    )
}

@Composable
private fun PunchEditorDialog(
    date: LocalDate,
    record: PunchRecord?,
    onDismiss: () -> Unit,
    onSave: (Long?, Long?) -> Unit
) {
    var clockInMillis by remember(date, record?.updatedAtMillis) { mutableStateOf(record?.clockInMillis) }
    var clockOutMillis by remember(date, record?.updatedAtMillis) { mutableStateOf(record?.clockOutMillis) }
    var showTimeRangePicker by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = DialogShape,
        title = {
            Text(
                if (record == null) "添加工时" else "编辑工时",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(date.format(DateTimeFormatter.ofPattern("yyyy年M月d日")))
                TimeEditRow(
                    label = "上班",
                    value = clockInMillis,
                    onPick = { showTimeRangePicker = true },
                    onClear = { clockInMillis = null }
                )
                TimeEditRow(
                    label = "下班",
                    value = clockOutMillis,
                    onPick = { showTimeRangePicker = true },
                    onClear = { clockOutMillis = null }
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(clockInMillis, clockOutMillis) },
                enabled = clockInMillis != null || clockOutMillis != null
            ) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
    if (showTimeRangePicker) {
        WorkTimeRangeBottomSheet(
            date = date,
            initialClockInMillis = clockInMillis,
            initialClockOutMillis = clockOutMillis,
            onDismiss = { showTimeRangePicker = false },
            onConfirm = { clockIn, clockOut ->
                clockInMillis = clockIn
                clockOutMillis = clockOut
                showTimeRangePicker = false
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WorkTimeRangeBottomSheet(
    date: LocalDate,
    initialClockInMillis: Long?,
    initialClockOutMillis: Long?,
    onDismiss: () -> Unit,
    onConfirm: (Long, Long) -> Unit
) {
    val initialClockIn = initialClockInMillis?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalTime() }
    val initialClockOut = initialClockOutMillis?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalTime() }
    var startHour by remember(initialClockInMillis) { mutableStateOf(initialClockIn?.hour ?: 9) }
    var startMinute by remember(initialClockInMillis) { mutableStateOf(initialClockIn?.minute ?: 0) }
    var endHour by remember(initialClockOutMillis) { mutableStateOf(initialClockOut?.hour ?: 18) }
    var endMinute by remember(initialClockOutMillis) { mutableStateOf(initialClockOut?.minute ?: 0) }
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { it != SheetValue.Hidden }
    )
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
        shape = SheetShape,
        dragHandle = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .height(SheetContentHeight)
                .padding(horizontal = 20.dp)
        ) {
            SheetHeader(title = "设置上下班时间", onClose = onDismiss)
            Spacer(Modifier.height(20.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                WheelNumberPicker(
                    value = startHour,
                    range = 0..23,
                    onValueChange = { startHour = it },
                    formatter = { value -> "%02d时".format(value) },
                    modifier = Modifier.weight(1f)
                )
                WheelNumberPicker(
                    value = startMinute,
                    range = 0..59,
                    onValueChange = { startMinute = it },
                    formatter = { value -> "%02d分".format(value) },
                    modifier = Modifier.weight(1f)
                )
                Text(
                    "至",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium
                )
                WheelNumberPicker(
                    value = endHour,
                    range = 0..23,
                    onValueChange = { endHour = it },
                    formatter = { value -> "%02d时".format(value) },
                    modifier = Modifier.weight(1f)
                )
                WheelNumberPicker(
                    value = endMinute,
                    range = 0..59,
                    onValueChange = { endMinute = it },
                    formatter = { value -> "%02d分".format(value) },
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(Modifier.weight(1f))
            SheetActionRow(
                onCancel = onDismiss,
                onConfirm = {
                        val clockIn = date.atTime(startHour, startMinute).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                        val clockOut = date.atTime(endHour, endMinute).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                        onConfirm(clockIn, clockOut)
                },
                modifier = Modifier.padding(bottom = 20.dp)
            )
        }
    }
}

@Composable
private fun TimeEditRow(label: String, value: Long?, onPick: () -> Unit, onClear: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(label, modifier = Modifier.weight(1f))
        TextButton(onClick = onPick) { Text(value?.toLocalTimeText() ?: "选择时间") }
        if (value != null) TextButton(onClick = onClear) { Text("清除") }
    }
}

@Composable
private fun DeleteRecordDialog(date: LocalDate, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = DialogShape,
        title = {
            Text(
                "删除这条记录？",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        text = { Text("${date.format(DateTimeFormatter.ofPattern("M月d日"))} 的上下班时间将被删除。") },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("删除", color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

private fun buildMonthCells(month: LocalDate): List<LocalDate?> {
    val first = month.withDayOfMonth(1)
    val leading = first.dayOfWeek.value - 1
    return List(leading) { null } + (1..first.lengthOfMonth()).map { first.withDayOfMonth(it) }
}

private fun todayText(): String {
    return LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy年M月d日"))
}

private fun formatDateRange(start: LocalDate, end: LocalDate): String {
    val formatter = DateTimeFormatter.ofPattern("yyyy年M月d日")
    return "${start.format(formatter)} - ${end.format(formatter)}"
}

@Composable
private fun currentTimeMillis(): Long {
    var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000 - nowMillis % 60_000)
            nowMillis = System.currentTimeMillis()
        }
    }
    return nowMillis
}

private fun LocalDate.withYearKeepingDay(year: Int): LocalDate {
    val lastDay = LocalDate.of(year, monthValue, 1).lengthOfMonth()
    return LocalDate.of(year, monthValue, minOf(dayOfMonth, lastDay))
}

private fun LocalDate.withMonthKeepingDay(month: Int): LocalDate {
    val lastDay = LocalDate.of(year, month, 1).lengthOfMonth()
    return LocalDate.of(year, month, minOf(dayOfMonth, lastDay))
}

@Composable
fun WorkPunchTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = androidx.compose.material3.lightColorScheme(
            primary = Color(0xFF167A5A),
            onPrimary = Color.White,
            primaryContainer = Color(0xFFDDEFE5),
            onPrimaryContainer = Color(0xFF0C513A),
            secondary = Color(0xFF66734A),
            secondaryContainer = Color(0xFFE8EFD7),
            tertiary = Color(0xFF9A5A1F),
            background = Color(0xFFF6F7F8),
            surface = Color(0xFFFFFFFF),
            surfaceVariant = Color(0xFFF1F4F1),
            onSurface = Color(0xFF1F1F1F),
            onSurfaceVariant = Color(0xFF666D68),
            outline = Color(0xFFB8C0BA),
            outlineVariant = Color(0xFFE4E8E5)
        ),
        content = content
    )
}
