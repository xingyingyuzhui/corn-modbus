package io.github.xingyingyuzhui.modbusmobile.ui.panel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.xingyingyuzhui.modbusmobile.application.panel.CurveConfig
import io.github.xingyingyuzhui.modbusmobile.application.panel.CurveForm
import io.github.xingyingyuzhui.modbusmobile.application.panel.CurveResult
import io.github.xingyingyuzhui.modbusmobile.application.panel.FormResult
import io.github.xingyingyuzhui.modbusmobile.application.panel.SampleHistory
import io.github.xingyingyuzhui.modbusmobile.application.panel.PanelRuntime
import io.github.xingyingyuzhui.modbusmobile.application.panel.PointDefinition
import io.github.xingyingyuzhui.modbusmobile.application.panel.WidgetForm
import io.github.xingyingyuzhui.modbusmobile.application.panel.WidgetKinds
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import io.github.xingyingyuzhui.modbusmobile.application.panel.PointSnapshot
import io.github.xingyingyuzhui.modbusmobile.application.panel.PollStats
import io.github.xingyingyuzhui.modbusmobile.application.panel.Project
import io.github.xingyingyuzhui.modbusmobile.application.panel.ProjectEdits
import io.github.xingyingyuzhui.modbusmobile.application.panel.ProjectLoad
import io.github.xingyingyuzhui.modbusmobile.application.panel.ProjectRepository
import io.github.xingyingyuzhui.modbusmobile.application.panel.ProjectSave
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface PanelUiState {
    data object Loading : PanelUiState

    /** [warnings] 是读文件时的提醒（忽略 / 保留了读不懂的组件、用了备份、只读等）。 */
    data class Ready(val project: Project, val warnings: List<String>, val readOnly: Boolean) : PanelUiState

    data class Corrupt(val reason: String, val copySaved: Boolean) : PanelUiState
}

enum class EditError { BLANK_NAME, SAVE_FAILED, READ_ONLY }

/**
 * 编辑模式的草稿：所有修改都先改 [draft]，点“完成”才写盘，点 ✕ 放弃（有修改会先确认）。
 * 编辑模式不发任何写入请求（面板现在也没有写入类组件）。
 */
data class EditState(
    val draft: Project,
    val panelId: String,
    val dirty: Boolean,
    val saving: Boolean = false,
    val error: EditError? = null,
)

/**
 * 添加 / 编辑组件向导的状态（挂在编辑草稿上）。[step]：1 选类型、2 配置、3 外观。
 * [editingWidgetId] 非空 = 改已有组件（从第 2 步开始）。[showErrors]：点过“下一步”后才显示红字。
 */
data class WizardState(
    val step: Int,
    val form: WidgetForm,
    val widgetId: String,
    val pointId: String,
    val editingWidgetId: String? = null,
    val original: WidgetForm? = null,
    val showErrors: Boolean = false,
    /** 曲线组件的表单（选了曲线才有；此时 [form] 不用）。 */
    val curve: CurveForm? = null,
    val originalCurve: CurveForm? = null,
    /** 曲线：正在编辑哪个系列（底部弹窗打开）；null = 没打开。 */
    val sheet: Int? = null,
    /** 曲线：系列的“数据点位”页（从弹窗进入）。 */
    val pointPage: Boolean = false,
    /** 下一步 / 完成没通过校验的次数；界面据此把第一个错误滚进可见区域。 */
    val errorAttempt: Int = 0,
) {
    val dirty: Boolean get() = if (curve != null) originalCurve == null || curve != originalCurve else original == null || form != original

    /** 改已有组件时，有没有改动（决定返回时要不要确认放弃）。 */
    val changed: Boolean get() = editingWidgetId != null && if (curve != null) curve != originalCurve else form != original

    fun result(): FormResult = form.build(widgetId, pointId)

    /** 第 2 步「数据」只校验读什么 / 写什么（名称、阈值等在第 3 步）。 */
    fun dataResult(): FormResult = form.buildData(widgetId, pointId)

    /** 当前这一步该显示的校验结果：第 2 步不算显示类字段。 */
    fun stepResult(): FormResult = if (step == 2) dataResult() else result()

    /** 有没有“数据”这一步的错误（决定完成失败时退回第 2 步还是留在第 3 步）。 */
    val dataOk: Boolean get() = if (curve != null) curve.buildData(widgetId).ok else dataResult().ok

    fun curveResult(): CurveResult = (curve ?: CurveForm()).build(widgetId)
}

const val PREVIEW_POINT_ID = "preview"

/** [PanelRuntime.setActive] 的使用者名：面板页 / 曲线全屏页。 */
const val PANEL_OWNER = "panel"
const val CURVE_OWNER = "curve"

/** 面板页状态。P3：读出工程文件，把点位交给轮询调度器，显示每个点位的最新值和质量；编辑在后续 PR。[repository] 为空（截图 / 单测）按空工程处理。 */
class PanelViewModel(
    private val repository: ProjectRepository?,
    private val runtime: PanelRuntime? = null,
    private val io: CoroutineDispatcher = Dispatchers.IO,
    /** 开关 / 按钮写设备要用的会话；为空（截图 / 单测）= 没有写入能力。 */
    session: io.github.xingyingyuzhui.modbusmobile.application.SessionManager? = null,
) : ViewModel() {
    private val _state = MutableStateFlow<PanelUiState>(PanelUiState.Loading)
    val state: StateFlow<PanelUiState> = _state.asStateFlow()
    val snapshots: StateFlow<Map<String, PointSnapshot>> = runtime?.store?.snapshots ?: MutableStateFlow(emptyMap())
    val stats: StateFlow<PollStats?> = runtime?.scheduler?.stats ?: MutableStateFlow(null)

    val paused: StateFlow<Boolean> = runtime?.scheduler?.paused ?: MutableStateFlow(false)

    private val _selectedPanelId = MutableStateFlow<String?>(null)

    /** 当前选中的面板 id；还没选或已不存在时由界面回落到第一个面板。 */
    val selectedPanelId: StateFlow<String?> = _selectedPanelId.asStateFlow()

    fun selectPanel(id: String) {
        _selectedPanelId.value = id
    }

    fun togglePause() {
        runtime?.scheduler?.setPaused(!paused.value)
    }

    /** 页面可见（且应用在前台）时才轮询。 */
    fun setActive(value: Boolean, owner: String = PANEL_OWNER) {
        runtime?.setActive(owner, value)
    }

    init {
        viewModelScope.launch { loadProject() }
    }

    private suspend fun loadProject() {
        val result = if (repository == null) ProjectLoad.Loaded(Project.empty()) else withContext(io) { repository.load() }
        _state.value = when (result) {
            is ProjectLoad.Loaded -> {
                runtime?.apply(result.project)
                PanelUiState.Ready(result.project, result.warnings, result.readOnly)
            }
            is ProjectLoad.Corrupt -> PanelUiState.Corrupt(result.reason, result.corruptCopy != null)
        }
    }

    // ---- 编辑模式 ----

    private val _edit = MutableStateFlow<EditState?>(null)
    val edit: StateFlow<EditState?> = _edit.asStateFlow()
    private var editOriginal: Project? = null

    /** 能不能进编辑模式：有存储位置、文件可读写（新版本写的文件只读）。 */
    val canEdit: Boolean get() = repository != null

    fun startEdit(currentPanelId: String?) {
        writer?.dismiss() // 进编辑模式时，还没确认的写入作废
        val ready = _state.value as? PanelUiState.Ready ?: return
        if (ready.readOnly || repository == null) return
        val project = ready.project
        val panelId = project.panels.firstOrNull { it.id == currentPanelId }?.id ?: project.panels.firstOrNull()?.id
        editOriginal = project
        if (panelId == null) {
            // 空工程：先建一个面板，才有东西可以改名 / 调列数
            val (withPanel, id) = ProjectEdits.addPanel(project)
            _edit.value = EditState(withPanel, id, dirty = true)
        } else {
            _edit.value = EditState(project, panelId, dirty = false)
        }
    }

    fun cancelEdit() {
        cancelWizard()
        _edit.value = null
        editOriginal = null
    }

    private fun change(transform: (Project, String) -> Project) {
        val e = _edit.value ?: return
        if (e.saving) return
        val next = transform(e.draft, e.panelId)
        _edit.value = e.copy(draft = next, dirty = next != editOriginal, error = null)
    }

    fun rename(name: String) = change { p, id -> ProjectEdits.renamePanel(p, id, name) }
    fun setColumns(columns: Int) = change { p, id -> ProjectEdits.setColumns(p, id, columns) }
    fun moveWidget(widgetId: String, targetId: String) = change { p, id -> ProjectEdits.moveWidget(p, id, widgetId, targetId) }
    fun setSpan(widgetId: String, columns: Int, rows: Int) = change { p, id -> ProjectEdits.setSpan(p, id, widgetId, columns, rows) }
    fun deleteWidget(widgetId: String) = change { p, id -> ProjectEdits.deleteWidget(p, id, widgetId) }
    fun deleteHiddenWidgets() = change { p, id -> ProjectEdits.deleteHidden(p, id) }

    fun selectEditPanel(id: String) {
        val e = _edit.value ?: return
        if (e.draft.panels.any { it.id == id }) _edit.value = e.copy(panelId = id)
    }

    fun addPanel() {
        val e = _edit.value ?: return
        if (e.saving) return
        val (next, id) = ProjectEdits.addPanel(e.draft)
        _edit.value = e.copy(draft = next, panelId = id, dirty = true, error = null)
    }

    fun deletePanel() {
        val e = _edit.value ?: return
        if (e.saving) return
        val next = ProjectEdits.deletePanel(e.draft, e.panelId)
        if (next == e.draft) return
        val panelId = next.panels.first().id
        _edit.value = e.copy(draft = next, panelId = panelId, dirty = next != editOriginal, error = null)
    }

    fun save() {
        val e = _edit.value ?: return
        if (e.saving || repository == null) return
        if (e.draft.panels.any { !ProjectEdits.isValidName(it.name) }) {
            _edit.value = e.copy(error = EditError.BLANK_NAME)
            return
        }
        if (!e.dirty) {
            selectPanel(e.panelId)
            cancelEdit()
            return
        }
        _edit.value = e.copy(saving = true, error = null)
        viewModelScope.launch {
            val result = withContext(io) { repository.save(e.draft) }
            val current = _edit.value ?: return@launch
            when (result) {
                ProjectSave.Saved -> {
                    val warnings = (_state.value as? PanelUiState.Ready)?.warnings.orEmpty()
                    _state.value = PanelUiState.Ready(e.draft, warnings, readOnly = false)
                    runtime?.apply(e.draft)
                    _selectedPanelId.value = current.panelId
                    cancelEdit()
                }
                ProjectSave.ReadOnly -> _edit.value = current.copy(saving = false, error = EditError.READ_ONLY)
                is ProjectSave.Failed -> _edit.value = current.copy(saving = false, error = EditError.SAVE_FAILED)
            }
        }
    }

    // ---- 添加 / 编辑组件向导 ----

    private val _wizard = MutableStateFlow<WizardState?>(null)
    val wizard: StateFlow<WizardState?> = _wizard.asStateFlow()

    /** 开关 / 按钮的写入入口（确认弹窗、读后写、广播红色确认都在里面）；编辑模式和向导里一律拒绝。 */
    val writer: PanelWriteController? = session?.let {
        PanelWriteController(it, viewModelScope, editing = { _edit.value != null || _wizard.value != null }, snapshotOf = { id -> snapshots.value[id] })
    }

    private var previewInstance: io.github.xingyingyuzhui.modbusmobile.application.panel.PointPreview? = null

    private fun preview() = previewInstance ?: runtime?.newPreview(viewModelScope)?.also { previewInstance = it }
    private var previewJob: Job? = null

    /** 向导预览读到的值（key = [PREVIEW_POINT_ID]）；没有运行时 / 没有预览能力时恒为空。 */
    val previewSnapshots: StateFlow<Map<String, PointSnapshot>> get() = preview()?.snapshots ?: noPreview

    private val noPreview = MutableStateFlow<Map<String, PointSnapshot>>(emptyMap())

    /** 向导能不能用实时预览（有运行时、主站会话由界面判断）。 */
    val hasPreview: Boolean get() = runtime != null

    /** [widgetId] 为空 = 添加新组件（从选类型开始）；否则改这个组件（直接进第 2 步）。必须在编辑模式里。 */
    fun startWizard(widgetId: String? = null) {
        val e = _edit.value ?: return
        if (e.saving) return
        val (newWidgetId, newPointId) = ProjectEdits.newIds(e.draft)
        preview() // 先建好：界面按这个流收预览读数
        if (widgetId == null) {
            _wizard.value = WizardState(1, WidgetForm(), newWidgetId, newPointId)
            return
        }
        val widget = e.draft.panels.firstOrNull { it.id == e.panelId }?.widgets?.firstOrNull { it.id == widgetId } ?: return
        if (widget.kind == WidgetKinds.CURVE) {
            val curve = CurveForm.fromWidget(widget) ?: return
            _wizard.value = WizardState(2, WidgetForm(), widget.id, newPointId, editingWidgetId = widget.id, curve = curve, originalCurve = curve)
            schedulePreview(WidgetForm(), curve, immediate = true)
            return
        }
        val form = WidgetForm.fromWidget(widget) ?: return
        _wizard.value = WizardState(2, form, widget.id, widget.point?.id ?: newPointId, editingWidgetId = widget.id, original = form)
        schedulePreview(form, immediate = true)
    }

    fun wizardSelectKind(kind: String) {
        val w = _wizard.value ?: return
        if (kind !in WidgetKinds.known) return
        if (kind == WidgetKinds.CURVE) {
            val curve = CurveForm()
            _wizard.value = w.copy(step = 2, form = WidgetForm(), curve = curve, sheet = null, pointPage = false, showErrors = false)
            schedulePreview(WidgetForm(), curve, immediate = true)
            return
        }
        val form = WidgetForm.forKind(kind)
        _wizard.value = w.copy(step = 2, form = form, curve = null, sheet = null, pointPage = false, showErrors = false)
        schedulePreview(form, immediate = true)
    }

    fun wizardForm(form: WidgetForm) {
        val w = _wizard.value ?: return
        _wizard.value = w.copy(form = form)
        schedulePreview(form)
    }

    // ---- 曲线向导 ----

    /** 曲线表单整体替换（底部弹窗 / 数据点位页 / 配置页的改动都走这里）。 */
    fun wizardCurve(curve: CurveForm) {
        val w = _wizard.value ?: return
        if (w.curve == null) return
        _wizard.value = w.copy(curve = curve)
        schedulePreview(w.form, curve)
    }

    /** “＋ 添加系列”：新增一个系列并打开它的编辑弹窗。最多 8 个。 */
    fun wizardAddSeries() {
        val w = _wizard.value ?: return
        val curve = w.curve ?: return
        if (curve.series.size >= CurveConfig.MAX_SERIES) return
        val project = _edit.value?.draft ?: Project.empty()
        val id = ProjectEdits.newPointId(project, extraUsed = curve.series.map { it.pointId }.toSet())
        val next = curve.addSeries(id)
        _wizard.value = w.copy(curve = next, sheet = next.series.lastIndex, pointPage = false)
        schedulePreview(w.form, next)
    }

    fun wizardOpenSeries(index: Int) {
        val w = _wizard.value ?: return
        if (w.curve == null || index !in w.curve.series.indices) return
        _wizard.value = w.copy(sheet = index, pointPage = false)
    }

    fun wizardOpenSeriesPoint() {
        val w = _wizard.value ?: return
        if (w.sheet == null) return
        _wizard.value = w.copy(pointPage = true)
    }

    fun wizardDeleteSeries(index: Int) {
        val w = _wizard.value ?: return
        val curve = w.curve ?: return
        val next = curve.removeSeries(index)
        _wizard.value = w.copy(curve = next, sheet = null, pointPage = false)
        schedulePreview(w.form, next)
    }

    fun wizardNext() {
        val w = _wizard.value ?: return
        if (w.sheet != null || w.pointPage) return
        when (w.step) {
            1 -> Unit
            2 -> {
                // v3：第 2 步只管数据；名称 / 阈值 / 坐标轴等显示类字段在第 3 步才校验
                _wizard.value = if (w.dataOk) w.copy(step = 3, showErrors = false) else w.copy(showErrors = true, errorAttempt = w.errorAttempt + 1)
            }
            else -> commitWizard()
        }
    }

    /** 上一步；返回 false 表示已经在向导的第一页（调用方决定是否要退出向导）。 */
    fun wizardBack(): Boolean {
        val w = _wizard.value ?: return false
        if (w.pointPage) { _wizard.value = w.copy(pointPage = false); return true }
        if (w.sheet != null) { _wizard.value = w.copy(sheet = null); return true }
        val first = if (w.editingWidgetId != null) 2 else 1
        if (w.step <= first) return false
        _wizard.value = w.copy(step = w.step - 1)
        return true
    }

    fun cancelWizard() {
        previewJob?.cancel()
        previewInstance?.close()
        _wizard.value = null
    }

    /** 把表单变成组件放进编辑草稿（添加到当前面板末尾 / 替换原组件）。还没“完成”之前不写盘。 */
    fun commitWizard() {
        val w = _wizard.value ?: return
        val widget = if (w.curve != null) w.curveResult().widget else w.result().widget
        if (widget == null) {
            // 数据有错退回第 2 步；只是显示类字段（名称、阈值……）没填好就留在第 3 步，滚到第一个错误
            _wizard.value = w.copy(step = if (w.dataOk) 3 else 2, showErrors = true, errorAttempt = w.errorAttempt + 1)
            return
        }
        if (w.editingWidgetId == null) {
            change { p, id -> ProjectEdits.addWidget(p, id, widget) }
        } else {
            change { p, id -> ProjectEdits.replaceWidget(p, id, widget) }
        }
        cancelWizard()
    }

    private fun schedulePreview(form: WidgetForm, curve: CurveForm? = _wizard.value?.curve, immediate: Boolean = false) {
        val target = preview() ?: return
        previewJob?.cancel()
        previewJob = viewModelScope.launch {
            if (!immediate) delay(PREVIEW_DEBOUNCE_MS)
            if (curve != null) {
                val points = curve.series.mapNotNull { it.build().widget?.point }
                target.setPoints(points)
                target.setActive(points.isNotEmpty())
            } else {
                // 按钮的点位只用来写，向导预览不读它（更不会写）
                // 按钮的 points 为空（只用来写），预览不读它；寄存器表预览读“preview#行号”这些行点位
                // 预览只看“读什么”：名称 / 阈值等显示类字段还没填也照样读（forPreview 用占位）
                val points = form.forPreview().build("preview", PREVIEW_POINT_ID).widget?.points.orEmpty()
                target.setPoints(points)
                target.setActive(points.isNotEmpty())
            }
        }
    }

    /** 曲线向导预览的样本历史；没有运行时 / 没有预览能力时为空。 */
    val previewHistory: SampleHistory? get() = preview()?.history

    /** 面板页曲线卡用的样本历史。 */
    val history: SampleHistory? get() = runtime?.history

    /** 向导页离开前台 / 回到前台（切走应用）时暂停 / 恢复预览读取。 */
    fun setPreviewActive(value: Boolean) {
        val w = _wizard.value
        if (!value || w == null) previewInstance?.setActive(false) else schedulePreview(w.form, w.curve, immediate = true)
    }

    override fun onCleared() {
        previewInstance?.close()
    }

    // 放在类末尾：用到的编辑 / 写入状态都已初始化。
    init {
        // 「我的 → 导入」替换了工程文件：丢掉编辑和待确认的写入，按新文件重读（导入前已提示）。
        repository?.revision?.let { revision ->
            viewModelScope.launch {
                revision.drop(1).collect {
                    writer?.dismiss()
                    cancelEdit()
                    loadProject()
                }
            }
        }
    }

    private companion object {
        const val PREVIEW_DEBOUNCE_MS = 400L
    }
}
