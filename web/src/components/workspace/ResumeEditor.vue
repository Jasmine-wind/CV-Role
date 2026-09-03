<script setup lang="ts">
import { ElMessage } from 'element-plus'
import { computed, ref } from 'vue'
import BulletSuggestionCard from '@/components/workspace/BulletSuggestionCard.vue'
import type {
  ResumeDocument,
  ResumeDocumentBasics,
  ResumeDocumentEntry,
  ResumeDocumentSection,
} from '@/types/resume-document'
import type { BulletSuggestIntent } from '@/types/workspace'
import type { BulletSuggestController } from '@/utils/useBulletSuggest'

const props = defineProps<{
  document: ResumeDocument
  suggest?: BulletSuggestController | null
  suggestEnabled?: boolean
  /** 草稿未保存 / 保存中 / 失败 / 冲突时禁止发起 Suggest。 */
  suggestLocked?: boolean
}>()

const emit = defineEmits<{
  change: [document: ResumeDocument]
}>()

const LIMITS = {
  name: 100,
  contact: 200,
  sectionTitle: 100,
  entryField: 200,
  bullet: 4000,
  contacts: 20,
  entriesPerSection: 100,
  bulletsPerEntry: 100,
}

const newId = () => crypto.randomUUID()

const compactContactRows = (contacts: ResumeDocumentBasics['contacts']) => {
  const seen = new Set<string>()
  let emptyContactKept = false
  return contacts.filter((contact) => {
    const value = contact.value?.trim() ?? ''
    if (!value) {
      if (emptyContactKept) return false
      emptyContactKept = true
      return true
    }
    const key = `${contact.type || 'OTHER'}\u0000${value}`
    if (seen.has(key)) return false
    seen.add(key)
    return true
  })
}

const visibleContacts = computed(() =>
  compactContactRows(props.document.basics.contacts ?? []),
)

const draggedSectionId = ref<string | null>(null)
const editingSectionTitle = ref<string | null>(null)
const expandedEntryFields = ref<Set<string>>(new Set())

/** 深拷贝后原地修改再整体发出，保证父级始终收到不可变的新文档。
 * 父级传入的 document 是 Vue reactive proxy，structuredClone 无法克隆 Proxy，
 * 与 useWorkspaceEditor 一致使用 JSON 克隆。 */
const mutate = (mutator: (doc: ResumeDocument) => void) => {
  const next = JSON.parse(JSON.stringify(props.document)) as ResumeDocument
  mutator(next)
  next.basics.contacts = compactContactRows(next.basics.contacts ?? [])
  emit('change', next)
}

const updateBasics = (mutator: (basics: ResumeDocumentBasics) => void) => {
  mutate((doc) => mutator(doc.basics))
}

const updateSection = (sectionId: string, mutator: (section: ResumeDocumentSection) => void) => {
  mutate((doc) => {
    const section = doc.sections.find((item) => item.id === sectionId)
    if (section) mutator(section)
  })
}

const updateEntry = (
  sectionId: string,
  entryId: string,
  mutator: (entry: ResumeDocumentEntry) => void,
) => {
  updateSection(sectionId, (section) => {
    const entry = section.entries.find((item) => item.id === entryId)
    if (entry) mutator(entry)
  })
}

const deleteContact = (contactId: string) => {
  updateBasics((basics) => {
    basics.contacts = basics.contacts.filter((item) => item.id !== contactId)
  })
}

const deleteEntry = (sectionId: string, entryId: string) => {
  updateSection(sectionId, (section) => {
    section.entries = section.entries.filter((item) => item.id !== entryId)
  })
}

const deleteBullet = (sectionId: string, entryId: string, bulletId: string) => {
  updateEntry(sectionId, entryId, (entry) => {
    entry.bullets = entry.bullets.filter((item) => item.id !== bulletId)
  })
}

const moveSection = (index: number, delta: number) => {
  mutate((doc) => {
    const target = index + delta
    if (target < 0 || target >= doc.sections.length) return
    const [section] = doc.sections.splice(index, 1)
    if (section) doc.sections.splice(target, 0, section)
  })
}

const startSectionDrag = (sectionId: string, event: DragEvent) => {
  draggedSectionId.value = sectionId
  if (event.dataTransfer) {
    event.dataTransfer.effectAllowed = 'move'
    event.dataTransfer.setData('text/plain', sectionId)
  }
}

const dropSection = (targetSectionId: string) => {
  const sourceSectionId = draggedSectionId.value
  draggedSectionId.value = null
  if (!sourceSectionId || sourceSectionId === targetSectionId) return
  mutate((doc) => {
    const sourceIndex = doc.sections.findIndex((section) => section.id === sourceSectionId)
    const targetIndex = doc.sections.findIndex((section) => section.id === targetSectionId)
    if (sourceIndex < 0 || targetIndex < 0) return
    const [section] = doc.sections.splice(sourceIndex, 1)
    if (section) doc.sections.splice(targetIndex, 0, section)
  })
}

const CONTACT_TYPES: Array<{ value: string; label: string }> = [
  { value: 'PHONE', label: '电话' },
  { value: 'EMAIL', label: '邮箱' },
  { value: 'WECHAT', label: '微信' },
  { value: 'QQ', label: 'QQ' },
  { value: 'LINKEDIN', label: 'LinkedIn' },
  { value: 'GITHUB', label: 'GitHub' },
  { value: 'WEBSITE', label: '个人网站' },
  { value: 'LOCATION', label: '所在地' },
  { value: 'OTHER', label: '其他' },
]

const contactPlaceholder = (type: string) => {
  switch (type) {
    case 'PHONE':
      return '例如 138 1234 5678'
    case 'EMAIL':
      return '例如 name@example.com'
    case 'GITHUB':
      return '例如 github.com/name'
    case 'WEBSITE':
      return '例如 your-site.com'
    default:
      return '联系方式内容'
  }
}

const setContactType = (contactId: string, type: string) => {
  updateBasics((basics) => {
    const target = basics.contacts.find((item) => item.id === contactId)
    if (target) {
      target.type = type
      target.label = null
    }
  })
}

const addContact = () => {
  if (props.document.basics.contacts.length >= LIMITS.contacts) {
    ElMessage.warning('联系方式数量已达上限')
    return
  }
  if (props.document.basics.contacts.some((contact) => !contact.value?.trim())) {
    ElMessage.warning('请先填写或删除当前空白联系方式')
    return
  }
  updateBasics((basics) => {
    basics.contacts.push({ id: newId(), type: 'OTHER', label: null, value: '' })
  })
}

/** 技能条目以「、」分隔的文本编辑，保存时拆分为结构化列表。 */
const skillItemsText = (entry: ResumeDocumentEntry) => (entry.skillItems ?? []).join('、')

const setSkillItemsText = (sectionId: string, entryId: string, value: string) => {
  updateEntry(sectionId, entryId, (target) => {
    target.skillItems = value
      .split(/[、，,]/)
      .map((item) => item.trim())
      .filter((item) => item.length > 0)
  })
}

const addEntry = (sectionId: string) => {
  const section = props.document.sections.find((item) => item.id === sectionId)
  if (!section) return
  if (section.entries.length >= LIMITS.entriesPerSection) {
    ElMessage.warning('单个章节的条目数量已达上限')
    return
  }
  updateSection(sectionId, (target) => {
    target.entries.push({
      id: newId(),
      organization: null,
      role: null,
      school: null,
      degree: null,
      major: null,
      startDate: null,
      endDate: null,
      location: null,
      group: null,
      skillItems: section.kind === 'SKILL' ? [] : null,
      bullets: [],
    })
  })
}

const addBullet = (sectionId: string, entryId: string) => {
  const section = props.document.sections.find((item) => item.id === sectionId)
  const entry = section?.entries.find((item) => item.id === entryId)
  if (!entry) return
  if (entry.bullets.length >= LIMITS.bulletsPerEntry) {
    ElMessage.warning('单个条目的内容数量已达上限')
    return
  }
  updateEntry(sectionId, entryId, (target) => {
    target.bullets.push({ id: newId(), text: '' })
  })
}

const sectionKindLabel = (kind: string) => {
  switch (kind) {
    case 'EXPERIENCE':
      return '工作经历'
    case 'PROJECT':
      return '项目经历'
    case 'EDUCATION':
      return '教育经历'
    case 'SKILL':
      return '技能'
    default:
      return '自定义内容'
  }
}

const sectionTitle = (section: ResumeDocumentSection) =>
  section.title.trim() || sectionKindLabel(section.kind)
const sectionEntryLabel = (kind: string) => {
  switch (kind) {
    case 'EXPERIENCE':
      return '工作经历'
    case 'PROJECT':
      return '项目经历'
    case 'EDUCATION':
      return '教育经历'
    case 'SKILL':
      return '技能组'
    default:
      return '内容'
  }
}

const bulletPlaceholder = (kind: string) => {
  switch (kind) {
    case 'EXPERIENCE':
      return '写下工作职责、技术实践或成果'
    case 'PROJECT':
      return '写下项目贡献、技术实践或成果'
    default:
      return '写下这段内容'
  }
}

const hasOptionalEntryFields = (entry: ResumeDocumentEntry, kind: string) =>
  kind === 'EXPERIENCE' || kind === 'PROJECT' ? Boolean(entry.location?.trim()) : false

const isEntryFieldsExpanded = (entryId: string) => expandedEntryFields.value.has(entryId)
const toggleEntryFields = (entryId: string) => {
  const next = new Set(expandedEntryFields.value)
  if (next.has(entryId)) next.delete(entryId)
  else next.add(entryId)
  expandedEntryFields.value = next
}

const syncEntryFieldsOpen = (entryId: string, event: Event) => {
  const open = (event.target as HTMLDetailsElement).open
  const next = new Set(expandedEntryFields.value)
  if (open) next.add(entryId)
  else next.delete(entryId)
  expandedEntryFields.value = next
}

const beginSectionTitleEdit = (sectionId: string) => {
  editingSectionTitle.value = sectionId
}

const finishSectionTitleEdit = () => {
  editingSectionTitle.value = null
}

const addEntryLabel = (kind: string) => {
  switch (kind) {
    case 'EXPERIENCE':
      return '添加工作经历'
    case 'PROJECT':
      return '添加项目'
    case 'EDUCATION':
      return '添加教育经历'
    case 'SKILL':
      return '添加技能组'
    default:
      return '添加内容条目'
  }
}

const entryContentLabel = (kind: string) => {
  if (kind === 'SKILL') return '技能项'
  if (kind === 'EXPERIENCE') return '工作要点'
  if (kind === 'PROJECT') return '项目要点'
  return '内容'
}

const SUGGEST_INTENTS: Array<{ command: BulletSuggestIntent | 'CUSTOM'; label: string }> = [
  { command: 'JOB_TARGETED', label: '岗位定向优化' },
  { command: 'SIMPLIFY', label: '精简' },
  { command: 'TECHNICAL_DEPTH', label: '强化技术深度' },
  { command: 'HIGHLIGHT_OUTCOME', label: '突出成果' },
  { command: 'CUSTOM', label: '自定义要求' },
]

type SuggestCardMode = 'composing' | 'requesting' | 'ready' | 'stale' | 'rejected' | 'error'

const suggestActive = (bulletId: string) =>
  !!props.suggest && props.suggest.activeBulletId.value === bulletId

const suggestCardMode = (bulletId: string): SuggestCardMode | null => {
  const controller = props.suggest
  if (!controller || controller.activeBulletId.value !== bulletId) return null
  const phase = controller.phase.value
  if (phase === 'idle') return null
  if (phase === 'ready' && controller.candidateStale.value) return 'stale'
  return phase
}

const handleSuggestCommand = (bulletId: string, command: BulletSuggestIntent | 'CUSTOM') => {
  if (!props.suggest || props.suggest.busy.value) return
  if (props.suggestLocked) {
    ElMessage.warning('请先完成保存，再生成建议')
    return
  }
  if (command === 'CUSTOM') {
    props.suggest.startCustomCompose(bulletId)
    return
  }
  props.suggest.suggest(bulletId, command)
}
</script>

<template>
  <div class="resume-editor">
    <div class="resume-paper">
      <div class="resume-page-meta"><span>岗位定向简历 · 可编辑文档</span><span>内容来自当前简历</span></div>
    <section class="editor-block editor-basics">
      <header class="editor-block-header">
        <div>
          <h2>{{ document.basics.name || '未命名简历' }}</h2>
          <p>投递时会展示在简历顶部 · 点击字段即可编辑</p>
        </div>
      </header>
      <div class="basics-grid">
        <label class="editor-field">
          <span>姓名</span>
          <el-input
            :model-value="document.basics.name ?? ''"
            :maxlength="LIMITS.name"
            placeholder="你的姓名"
            @update:model-value="(value: string) => updateBasics((basics) => (basics.name = value))"
          />
        </label>

        <div v-for="contact in visibleContacts" :key="contact.id" class="contact-row">
          <label class="contact-type-field">
            <span>类型</span>
            <select
              class="contact-type"
              :value="contact.type || 'OTHER'"
              aria-label="联系方式类型"
              @change="
                (event: Event) =>
                  setContactType(contact.id, (event.target as HTMLSelectElement).value)
              "
            >
              <option v-for="option in CONTACT_TYPES" :key="option.value" :value="option.value">
                {{ option.label }}
              </option>
            </select>
          </label>
          <label class="editor-field contact-value-field">
            <span>{{
              CONTACT_TYPES.find((option) => option.value === contact.type)?.label || '联系方式'
            }}</span>
            <el-input
              :model-value="contact.value"
              :maxlength="LIMITS.contact"
              :placeholder="contactPlaceholder(contact.type)"
              @update:model-value="
                (value: string) =>
                  updateBasics((basics) => {
                    const target = basics.contacts.find((item) => item.id === contact.id)
                    if (target) target.value = value
                  })
              "
            />
          </label>
          <details class="inline-more">
            <summary>更多</summary>
            <div class="inline-more-menu">
              <button type="button" class="danger-action" @click="deleteContact(contact.id)">
                删除此联系方式
              </button>
            </div>
          </details>
        </div>

        <el-button class="add-control" size="small" @click="addContact">添加联系方式</el-button>

        <details class="supplement-details">
          <summary>
            <span>补充信息（可选）</span>
            <small v-if="document.basics.jobIntention || document.basics.highestEducation"
              >已填写</small
            >
          </summary>
          <div class="supplement-grid">
            <label class="editor-field">
              <span>求职意向（可选）</span>
              <el-input
                :model-value="document.basics.jobIntention ?? ''"
                :maxlength="LIMITS.entryField"
                placeholder="例如 Java 后端工程师"
                @update:model-value="
                  (value: string) => updateBasics((basics) => (basics.jobIntention = value))
                "
              />
            </label>
            <label class="editor-field">
              <span>最高学历（可选）</span>
              <el-input
                :model-value="document.basics.highestEducation ?? ''"
                :maxlength="LIMITS.entryField"
                placeholder="例如 本科"
                @update:model-value="
                  (value: string) => updateBasics((basics) => (basics.highestEducation = value))
                "
              />
            </label>
          </div>
        </details>
      </div>
    </section>

    <section
      v-for="(section, sectionIndex) in document.sections"
      :key="section.id"
      class="editor-block"
      @dragover.prevent
      @drop.prevent="dropSection(section.id)"
    >
      <header class="editor-block-header">
        <div class="section-heading">
          <span v-if="section.title.trim() !== sectionKindLabel(section.kind)" class="section-kind">
            {{ sectionKindLabel(section.kind) }}
          </span>
          <template v-if="editingSectionTitle === section.id">
            <el-input
              class="section-title-input"
              :model-value="section.title"
              :maxlength="LIMITS.sectionTitle"
              aria-label="章节标题"
              @update:model-value="
                (value: string) => updateSection(section.id, (target) => (target.title = value))
              "
              @blur="finishSectionTitleEdit"
            />
          </template>
          <button
            v-else
            type="button"
            class="section-title-display"
            :aria-label="`编辑${sectionTitle(section)}标题`"
            @click="beginSectionTitleEdit(section.id)"
          >
            {{ sectionTitle(section) }}
          </button>
        </div>
        <details class="section-more">
          <summary>更多</summary>
          <div class="section-more-menu">
            <button
              class="section-drag-handle"
              type="button"
              draggable="true"
              aria-label="拖拽调整章节顺序"
              @dragstart="startSectionDrag(section.id, $event)"
              @dragend="draggedSectionId = null"
            >
              拖动排序
            </button>
            <button
              type="button"
              :disabled="sectionIndex === 0"
              @click="moveSection(sectionIndex, -1)"
            >
              上移
            </button>
            <button
              type="button"
              :disabled="sectionIndex === document.sections.length - 1"
              @click="moveSection(sectionIndex, 1)"
            >
              下移
            </button>
          </div>
        </details>
      </header>

      <p v-if="section.entries.length === 0" class="editor-empty">
        该章节暂时没有内容，可以添加{{ sectionEntryLabel(section.kind) }}。
      </p>

      <article v-for="entry in section.entries" :key="entry.id" class="editor-entry">
        <template v-if="section.kind === 'SKILL'">
          <div class="skill-grid">
            <label class="editor-field">
              <span>技能分组</span>
              <el-input
                :model-value="entry.group ?? ''"
                :maxlength="LIMITS.entryField"
                placeholder="例如 后端技术"
                @update:model-value="
                  (value: string) =>
                    updateEntry(section.id, entry.id, (target) => (target.group = value))
                "
              />
            </label>
            <label class="editor-field">
              <span>技能项</span>
              <el-input
                :model-value="skillItemsText(entry)"
                :maxlength="LIMITS.bullet"
                placeholder="用顿号分隔，例如 Java、Spring Boot"
                @update:model-value="
                  (value: string) => setSkillItemsText(section.id, entry.id, value)
                "
              />
            </label>
          </div>
        </template>

        <template v-else-if="section.kind === 'EDUCATION'">
          <div class="entry-grid">
            <label class="editor-field">
              <span>学校</span>
              <el-input
                :model-value="entry.school ?? ''"
                :maxlength="LIMITS.entryField"
                placeholder="学校名称"
                @update:model-value="
                  (value: string) =>
                    updateEntry(section.id, entry.id, (target) => (target.school = value))
                "
              />
            </label>
            <label class="editor-field">
              <span>学历</span>
              <el-input
                :model-value="entry.degree ?? ''"
                :maxlength="LIMITS.entryField"
                placeholder="例如 本科"
                @update:model-value="
                  (value: string) =>
                    updateEntry(section.id, entry.id, (target) => (target.degree = value))
                "
              />
            </label>
            <label class="editor-field">
              <span>专业</span>
              <el-input
                :model-value="entry.major ?? ''"
                :maxlength="LIMITS.entryField"
                placeholder="专业名称"
                @update:model-value="
                  (value: string) =>
                    updateEntry(section.id, entry.id, (target) => (target.major = value))
                "
              />
            </label>
            <div class="date-grid">
              <label class="editor-field">
                <span>开始时间</span>
                <el-input
                  :model-value="entry.startDate ?? ''"
                  :maxlength="LIMITS.entryField"
                  placeholder="例如 2018.09"
                  @update:model-value="
                    (value: string) =>
                      updateEntry(section.id, entry.id, (target) => (target.startDate = value))
                  "
                />
              </label>
              <label class="editor-field">
                <span>结束时间</span>
                <el-input
                  :model-value="entry.endDate ?? ''"
                  :maxlength="LIMITS.entryField"
                  placeholder="例如 2022.06"
                  @update:model-value="
                    (value: string) =>
                      updateEntry(section.id, entry.id, (target) => (target.endDate = value))
                  "
                />
              </label>
            </div>
          </div>
        </template>

        <template v-else-if="section.kind === 'EXPERIENCE' || section.kind === 'PROJECT'">
          <div class="entry-grid">
            <label class="editor-field">
              <span>{{ section.kind === 'PROJECT' ? '项目名' : '公司' }}</span>
              <el-input
                :model-value="entry.organization ?? ''"
                :maxlength="LIMITS.entryField"
                :placeholder="section.kind === 'PROJECT' ? '项目名称' : '公司名称'"
                @update:model-value="
                  (value: string) =>
                    updateEntry(section.id, entry.id, (target) => (target.organization = value))
                "
              />
            </label>
            <label class="editor-field">
              <span>{{ section.kind === 'PROJECT' ? '角色' : '职位' }}</span>
              <el-input
                :model-value="entry.role ?? ''"
                :maxlength="LIMITS.entryField"
                :placeholder="section.kind === 'PROJECT' ? '项目角色（可选）' : '职位名称'"
                @update:model-value="
                  (value: string) =>
                    updateEntry(section.id, entry.id, (target) => (target.role = value))
                "
              />
            </label>
            <div class="date-grid">
              <label class="editor-field">
                <span>开始时间</span>
                <el-input
                  :model-value="entry.startDate ?? ''"
                  :maxlength="LIMITS.entryField"
                  placeholder="例如 2022.07"
                  @update:model-value="
                    (value: string) =>
                      updateEntry(section.id, entry.id, (target) => (target.startDate = value))
                  "
                />
              </label>
              <label class="editor-field">
                <span>结束时间</span>
                <el-input
                  :model-value="entry.endDate ?? ''"
                  :maxlength="LIMITS.entryField"
                  placeholder="例如 至今"
                  @update:model-value="
                    (value: string) =>
                      updateEntry(section.id, entry.id, (target) => (target.endDate = value))
                  "
                />
              </label>
            </div>
          </div>
          <details
            v-if="hasOptionalEntryFields(entry, section.kind) || isEntryFieldsExpanded(entry.id)"
            class="entry-optional-fields"
            :open="isEntryFieldsExpanded(entry.id)"
            @toggle="syncEntryFieldsOpen(entry.id, $event)"
          >
            <summary>更多字段</summary>
            <label class="editor-field">
              <span>地点</span>
              <el-input
                :model-value="entry.location ?? ''"
                :maxlength="LIMITS.entryField"
                placeholder="例如 上海"
                @update:model-value="
                  (value: string) =>
                    updateEntry(section.id, entry.id, (target) => (target.location = value))
                "
              />
            </label>
          </details>
          <button
            v-if="!hasOptionalEntryFields(entry, section.kind) && !isEntryFieldsExpanded(entry.id)"
            type="button"
            class="add-field-control"
            @click="toggleEntryFields(entry.id)"
          >
            添加更多字段
          </button>
        </template>

        <div v-if="section.kind !== 'SKILL'" class="entry-bullets-label">
          {{ entryContentLabel(section.kind) }}
        </div>
        <template v-if="section.kind !== 'SKILL'">
          <div v-for="bullet in entry.bullets" :key="bullet.id" class="bullet-block">
            <div class="bullet-line">
              <label class="bullet-field">
                <span class="sr-only">{{ entryContentLabel(section.kind) }}</span>
                <el-input
                  type="textarea"
                  :autosize="{ minRows: 1, maxRows: 6 }"
                  :model-value="bullet.text"
                  :maxlength="LIMITS.bullet"
                  :placeholder="bulletPlaceholder(section.kind)"
                  @update:model-value="
                    (value: string) =>
                      updateEntry(section.id, entry.id, (target) => {
                        const targetBullet = target.bullets.find((item) => item.id === bullet.id)
                        if (targetBullet) targetBullet.text = value
                      })
                  "
                />
              </label>
              <div class="bullet-actions">
                <el-dropdown
                  v-if="suggestEnabled && suggest"
                  trigger="click"
                  @command="
                    (command: unknown) =>
                      handleSuggestCommand(bullet.id, command as BulletSuggestIntent | 'CUSTOM')
                  "
                >
                  <el-button
                    class="bullet-suggest-button"
                    size="small"
                    :disabled="
                      suggest.busy.value || suggestActive(bullet.id) || !bullet.text.trim()
                    "
                  >
                    优化
                  </el-button>
                  <template #dropdown>
                    <el-dropdown-menu>
                      <el-dropdown-item
                        v-for="item in SUGGEST_INTENTS"
                        :key="item.command"
                        :command="item.command"
                      >
                        {{ item.label }}
                      </el-dropdown-item>
                    </el-dropdown-menu>
                  </template>
                </el-dropdown>
                <details class="inline-more bullet-more">
                  <summary>更多</summary>
                  <div class="inline-more-menu">
                    <button
                      type="button"
                      class="danger-action"
                      @click="deleteBullet(section.id, entry.id, bullet.id)"
                    >
                      删除此{{ entryContentLabel(section.kind) }}
                    </button>
                  </div>
                </details>
              </div>
            </div>
            <BulletSuggestionCard
              v-if="suggest && suggestCardMode(bullet.id)"
              :mode="suggestCardMode(bullet.id)!"
              :original-text="suggest.candidate.value?.originalText ?? bullet.text"
              :suggested-text="suggest.candidate.value?.suggestedText ?? null"
              :reason="suggest.candidate.value?.reason ?? null"
              :reject-message="suggest.rejectInfo.value?.message ?? null"
              :error-message="suggest.errorMessage.value ?? null"
              @apply="suggest?.apply()"
              @reject="suggest?.reject()"
              @regenerate="suggest?.regenerate()"
              @cancel="suggest?.cancelCompose()"
              @submit-custom="(text: string) => suggest?.submitCustom(bullet.id, text)"
            />
          </div>
        </template>

        <div v-if="section.kind !== 'SKILL'" class="entry-actions">
          <el-button size="small" @click="addBullet(section.id, entry.id)">
            添加{{ entryContentLabel(section.kind) }}
          </el-button>
        </div>
        <div class="entry-more-row">
          <details class="entry-more">
            <summary>条目操作</summary>
            <div class="entry-more-menu">
              <button
                type="button"
                class="danger-action"
                @click="deleteEntry(section.id, entry.id)"
              >
                删除此条目
              </button>
            </div>
          </details>
        </div>
      </article>

      <div class="section-footer">
        <el-button size="small" @click="addEntry(section.id)">{{
          addEntryLabel(section.kind)
        }}</el-button>
      </div>
    </section>
    <div class="resume-page-footer"><span>当前版本由你确认</span><span>编辑内容会自动保存</span></div>
    </div>
  </div>
</template>

<style scoped>
.resume-editor {
  display: grid;
  gap: 26px;
  min-width: 0;
}

.editor-block {
  display: grid;
  gap: 16px;
  min-width: 0;
  padding-bottom: 26px;
  border-bottom: 1px solid var(--app-border);
}

.editor-block:last-child {
  border-bottom: 0;
}

.editor-block-header {
  display: flex;
  align-items: start;
  justify-content: space-between;
  gap: 16px;
}

.editor-block-header h2 {
  margin: 0;
  color: var(--app-text);
  font-size: 18px;
  line-height: 1.4;
}

.editor-block-header p {
  margin: 4px 0 0;
  color: var(--app-text-secondary);
  font-size: 12px;
  line-height: 1.6;
}

.basics-grid,
.supplement-grid,
.entry-grid,
.skill-grid {
  display: grid;
  gap: 14px;
}

.basics-grid,
.supplement-grid {
  max-width: 760px;
}

.editor-field {
  display: grid;
  min-width: 0;
  gap: 6px;
  padding: 5px 7px 7px;
  border: 1px solid transparent;
  transition: border-color 140ms ease, background-color 140ms ease;
}

.editor-field:hover,
.editor-field:focus-within {
  border-color: var(--app-border-strong);
  background: var(--app-surface-soft);
}

.editor-field:focus-within {
  border-color: var(--app-primary);
  background: var(--app-primary-soft);
}

.editor-field > span,
.contact-type-field > span,
.entry-bullets-label {
  color: var(--app-text-secondary);
  font-size: 12px;
  font-weight: 700;
}

.contact-row {
  display: grid;
  grid-template-columns: 150px minmax(0, 1fr) auto;
  gap: 10px;
  align-items: end;
  max-width: 760px;
}

.contact-type-field {
  display: grid;
  gap: 6px;
  padding: 5px 7px 7px;
  border: 1px solid transparent;
  transition: border-color 140ms ease, background-color 140ms ease;
}

.contact-type-field:hover,
.contact-type-field:focus-within {
  border-color: var(--app-border-strong);
  background: var(--app-surface-soft);
}

.contact-type {
  width: 100%;
  height: 32px;
  border: 1px solid var(--app-border);
  border-radius: var(--app-radius-md);
  padding: 0 9px;
  color: var(--app-text);
  background: var(--app-surface);
}

.contact-type:focus-visible {
  outline: 2px solid var(--app-primary);
  outline-offset: 2px;
}

.contact-value-field {
  min-width: 0;
}

.add-control {
  justify-self: start;
}

.supplement-details,
.entry-optional-fields {
  max-width: 760px;
  border-top: 1px solid var(--app-border-soft);
  padding-top: 12px;
}

.supplement-details summary,
.entry-optional-fields summary,
.inline-more summary,
.section-more summary,
.entry-more summary {
  width: fit-content;
  color: var(--app-primary);
  font-size: 12px;
  font-weight: 700;
  cursor: pointer;
}

.supplement-details summary small {
  margin-left: 8px;
  color: var(--app-text-muted);
  font-weight: 500;
}

.supplement-grid {
  grid-template-columns: repeat(2, minmax(0, 1fr));
  margin-top: 12px;
}

.section-heading {
  display: grid;
  gap: 2px;
  min-width: 0;
}

.section-kind {
  color: var(--app-text-muted);
  font-size: 11px;
  font-weight: 700;
}

.section-title-display {
  max-width: 560px;
  overflow: hidden;
  border: 0;
  padding: 0;
  color: var(--app-text);
  font: inherit;
  font-size: 18px;
  font-weight: 750;
  line-height: 1.4;
  text-align: left;
  text-overflow: ellipsis;
  white-space: nowrap;
  background: transparent;
  cursor: text;
}

.section-title-display:hover {
  color: var(--app-primary);
}

.section-title-input {
  width: min(560px, 100%);
}

.section-more,
.entry-more,
.inline-more {
  position: relative;
  flex: 0 0 auto;
}

.section-more summary,
.entry-more summary,
.inline-more summary {
  list-style: none;
}

.section-more summary::-webkit-details-marker,
.entry-more summary::-webkit-details-marker,
.inline-more summary::-webkit-details-marker {
  display: none;
}

.section-more-menu,
.entry-more-menu,
.inline-more-menu {
  position: absolute;
  z-index: 5;
  top: calc(100% + 7px);
  right: 0;
  display: grid;
  min-width: 142px;
  gap: 2px;
  padding: 5px;
  border: 1px solid var(--app-border);
  border-radius: var(--app-radius-md);
  background: var(--app-surface);
  box-shadow: var(--app-shadow-soft);
}

.section-more-menu button,
.entry-more-menu button,
.inline-more-menu button {
  border: 0;
  padding: 8px 9px;
  color: var(--app-text-secondary);
  font: inherit;
  font-size: 12px;
  text-align: left;
  background: transparent;
  cursor: pointer;
}

.section-more-menu button:hover,
.section-more-menu button:focus-visible,
.entry-more-menu button:hover,
.entry-more-menu button:focus-visible,
.inline-more-menu button:hover,
.inline-more-menu button:focus-visible {
  color: var(--app-text);
  background: var(--app-surface-soft);
}

.section-more-menu button:disabled {
  color: var(--app-text-muted);
  cursor: not-allowed;
}

.section-drag-handle {
  cursor: grab !important;
}

.section-drag-handle:active {
  cursor: grabbing !important;
}

.editor-empty {
  margin: 0;
  color: var(--app-text-muted);
  font-size: 13px;
}

.editor-entry {
  display: grid;
  gap: 14px;
  min-width: 0;
  margin: 0 -8px;
  padding: 18px 8px 0;
  border: 1px solid transparent;
  border-top-color: var(--app-border-soft);
  transition: border-color 140ms ease, background-color 140ms ease;
}

.editor-entry:hover,
.editor-entry:focus-within {
  border-color: var(--app-border-strong);
  background: color-mix(in srgb, var(--app-surface-soft) 70%, transparent);
}

.entry-grid,
.skill-grid {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.date-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  grid-column: 1 / -1;
  gap: 10px;
}

.entry-optional-fields {
  display: grid;
  gap: 10px;
}

.entry-optional-fields .editor-field {
  max-width: 360px;
}

.add-field-control {
  justify-self: start;
  border: 0;
  padding: 0;
  color: var(--app-primary);
  font: inherit;
  font-size: 12px;
  cursor: pointer;
  background: transparent;
}

.entry-bullets-label {
  margin-top: 2px;
}

.bullet-block {
  display: grid;
  gap: 9px;
  min-width: 0;
}

.bullet-line {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 10px;
  align-items: start;
  border: 1px solid transparent;
  transition: border-color 140ms ease, background-color 140ms ease;
}

.bullet-line:hover,
.bullet-line:focus-within {
  border-color: var(--app-border-strong);
  background: var(--app-primary-soft);
}

.bullet-field {
  display: block;
  min-width: 0;
}

.bullet-actions {
  display: flex;
  align-items: center;
  gap: 10px;
  min-height: 32px;
  opacity: 0;
  transition: opacity 0.15s ease;
}

.bullet-line:hover .bullet-actions,
.bullet-line:focus-within .bullet-actions,
.bullet-actions:focus-within {
  opacity: 1;
}

.bullet-suggest-button {
  color: var(--app-primary);
}

.entry-actions,
.section-footer {
  display: flex;
}

.entry-actions {
  padding-top: 2px;
}

.entry-more-row {
  display: flex;
  justify-content: flex-end;
}

.danger-action {
  color: var(--app-danger) !important;
}

/* The editor keeps every structured field and action, but presents them as one document of record. */
.resume-editor {
  display: block;
  min-width: 0;
  padding: 14px 12px 38px;
  background: var(--app-stage);
}

.resume-paper {
  position: relative;
  width: min(760px, 100%);
  min-height: 850px;
  margin: 0 auto;
  padding: 30px 49px 58px;
  background: var(--app-document);
  border: 1px solid var(--app-border);
  box-shadow: var(--app-shadow-page);
}

.resume-page-meta,
.resume-page-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 18px;
  color: var(--app-text-muted);
  font-family: 'IBM Plex Mono', 'SFMono-Regular', Consolas, monospace;
  font-size: 8px;
  font-weight: 650;
  letter-spacing: 0.07em;
  text-transform: uppercase;
}

.resume-page-meta {
  padding-bottom: 20px;
  border-bottom: 1px solid var(--app-text);
}

.resume-page-footer {
  position: absolute;
  right: 49px;
  bottom: 20px;
  left: 49px;
  padding-top: 8px;
  border-top: 1px solid var(--app-border);
}

.editor-block {
  gap: 14px;
  padding-bottom: 24px;
  border-bottom-color: var(--app-border);
}

.editor-basics {
  padding-top: 24px;
}

.editor-block-header h2 {
  color: var(--app-text);
}

.editor-basics .editor-block-header {
  align-items: flex-end;
}

.editor-basics .editor-block-header h2 {
  margin-bottom: 6px;
  font-family: Georgia, 'Songti SC', serif;
  font-size: 34px;
  font-weight: 650;
  line-height: 1;
  letter-spacing: -0.05em;
}

.editor-basics .editor-block-header p {
  color: var(--app-accent);
  font-size: 11px;
}

.editor-field > span,
.contact-type-field > span,
.entry-bullets-label {
  color: var(--app-text-muted);
  font-family: 'IBM Plex Mono', 'SFMono-Regular', Consolas, monospace;
  font-size: 9px;
  font-weight: 700;
  letter-spacing: 0.04em;
  text-transform: uppercase;
}

:deep(.el-input__wrapper) {
  min-height: 30px;
  border-radius: 0;
  padding: 0;
  background: transparent;
  box-shadow: 0 1px 0 var(--app-border) !important;
}

:deep(.el-input__wrapper:hover),
:deep(.el-input__wrapper.is-focus) {
  box-shadow: 0 2px 0 var(--app-primary) !important;
}

:deep(.el-input__inner) {
  color: var(--app-text);
  font-size: 12px;
}

:deep(.el-textarea__inner) {
  min-height: 31px;
  border: 0;
  border-radius: 0;
  padding: 6px 7px;
  color: var(--app-text);
  font-size: 12px;
  line-height: 1.55;
  background: transparent;
  box-shadow: 0 1px 0 var(--app-border) !important;
  resize: vertical;
}

:deep(.el-textarea__inner:hover),
:deep(.el-textarea__inner:focus) {
  box-shadow: 0 2px 0 var(--app-primary) !important;
}

.contact-type {
  height: 30px;
  border-color: var(--app-border);
  border-radius: 0;
  padding: 0 7px;
  color: var(--app-text);
  font-size: 11px;
  background: transparent;
}

.section-title-display {
  color: var(--app-text);
  font-family: 'IBM Plex Mono', 'SFMono-Regular', Consolas, monospace;
  font-size: 10px;
  font-weight: 750;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.section-title-display:hover {
  color: var(--app-primary-active);
}

.section-kind {
  color: var(--app-text-muted);
  font-family: 'IBM Plex Mono', 'SFMono-Regular', Consolas, monospace;
  font-size: 9px;
  letter-spacing: 0.05em;
  text-transform: uppercase;
}

.editor-entry {
  gap: 12px;
  padding-top: 17px;
  border-top-color: var(--app-border-soft);
}

.entry-grid,
.skill-grid {
  column-gap: 18px;
  row-gap: 11px;
}

.entry-bullets-label {
  margin-top: 4px;
}

.bullet-line {
  gap: 8px;
}

.bullet-field {
  position: relative;
  padding-left: 14px;
}

.bullet-field::before {
  position: absolute;
  top: 10px;
  left: 3px;
  width: 4px;
  height: 4px;
  border-radius: 50%;
  background: var(--app-text-muted);
  content: '';
}

.bullet-actions {
  min-height: 30px;
}

.bullet-suggest-button {
  color: var(--app-primary-active);
}

.entry-actions,
.section-footer {
  padding-top: 3px;
}

.add-control,
.add-field-control,
.section-footer :deep(.el-button),
.entry-actions :deep(.el-button) {
  color: var(--app-text-secondary);
}

@media (max-width: 760px) {
  .contact-row,
  .entry-grid,
  .skill-grid,
  .supplement-grid {
    grid-template-columns: 1fr;
  }

  .contact-row {
    align-items: stretch;
  }

  .contact-row .inline-more {
    justify-self: start;
  }

  .date-grid {
    grid-column: auto;
  }

  .bullet-actions {
    opacity: 1;
    gap: 7px;
  }

  .section-title-display {
    max-width: calc(100vw - 110px);
  }
}
</style>
