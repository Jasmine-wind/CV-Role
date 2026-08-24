<script setup lang="ts">
import { ElMessage } from 'element-plus'
import { ref } from 'vue'
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
  sections: 30,
  entriesPerSection: 100,
  bulletsPerEntry: 100,
}

const newId = () => crypto.randomUUID()
const draggedSectionId = ref<string | null>(null)

/** 深拷贝后原地修改再整体发出，保证父级始终收到不可变的新文档。
 * 父级传入的 document 是 Vue reactive proxy，structuredClone 无法克隆 Proxy，
 * 与 useWorkspaceEditor 一致使用 JSON 克隆。 */
const mutate = (mutator: (doc: ResumeDocument) => void) => {
  const next = JSON.parse(JSON.stringify(props.document)) as ResumeDocument
  mutator(next)
  emit('change', next)
}

const updateBasics = (mutator: (basics: ResumeDocumentBasics) => void) => {
  mutate((doc) => mutator(doc.basics))
}

const updateSection = (sectionId: string, mutator: (section: ResumeDocumentSection) => void) => {
  mutate((doc) => {
    const section = doc.sections.find((item) => item.id === sectionId)
    if (section) {
      mutator(section)
    }
  })
}

const updateEntry = (
  sectionId: string,
  entryId: string,
  mutator: (entry: ResumeDocumentEntry) => void,
) => {
  updateSection(sectionId, (section) => {
    const entry = section.entries.find((item) => item.id === entryId)
    if (entry) {
      mutator(entry)
    }
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

const addContact = () => {
  if (props.document.basics.contacts.length >= LIMITS.contacts) {
    ElMessage.warning('基础信息字段数量已达上限')
    return
  }
  updateBasics((basics) => {
    basics.contacts.push({ id: newId(), label: '', value: '' })
  })
}

const addEntry = (sectionId: string) => {
  const section = props.document.sections.find((item) => item.id === sectionId)
  if (!section) {
    return
  }
  if (section.entries.length >= LIMITS.entriesPerSection) {
    ElMessage.warning('单个章节的条目数量已达上限')
    return
  }
  updateSection(sectionId, (target) => {
    target.entries.push({ id: newId(), heading: '', meta: '', bullets: [] })
  })
}

const addBullet = (sectionId: string, entryId: string) => {
  const section = props.document.sections.find((item) => item.id === sectionId)
  const entry = section?.entries.find((item) => item.id === entryId)
  if (!entry) {
    return
  }
  if (entry.bullets.length >= LIMITS.bulletsPerEntry) {
    ElMessage.warning('单个条目的要点数量已达上限')
    return
  }
  updateEntry(sectionId, entryId, (target) => {
    target.bullets.push({ id: newId(), text: '' })
  })
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
  // 未保存内容不得进入 Suggest：明确告知用户先完成保存，而不是静默禁用。
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
    <section class="editor-block">
      <header class="editor-block-header">
        <h3>基础信息</h3>
      </header>
      <div class="basics-grid">
        <label class="editor-field">
          <span>姓名</span>
          <el-input
            :model-value="document.basics.name ?? ''"
            :maxlength="LIMITS.name"
            placeholder="姓名"
            @update:model-value="(value: string) => updateBasics((basics) => (basics.name = value))"
          />
        </label>
        <div
          v-for="contact in document.basics.contacts"
          :key="contact.id"
          class="editor-field is-inline"
        >
          <el-input
            class="contact-label"
            :model-value="contact.label"
            :maxlength="LIMITS.contact"
            placeholder="字段名"
            @update:model-value="
              (value: string) =>
                updateBasics((basics) => {
                  const target = basics.contacts.find((item) => item.id === contact.id)
                  if (target) target.label = value
                })
            "
          />
          <el-input
            :model-value="contact.value"
            :maxlength="LIMITS.contact"
            placeholder="内容"
            @update:model-value="
              (value: string) =>
                updateBasics((basics) => {
                  const target = basics.contacts.find((item) => item.id === contact.id)
                  if (target) target.value = value
                })
            "
          />
          <el-button
            text
            type="danger"
            aria-label="删除基础信息字段"
            @click="
              updateBasics((basics) => {
                basics.contacts = basics.contacts.filter((item) => item.id !== contact.id)
              })
            "
          >
            删除
          </el-button>
        </div>
        <el-button size="small" @click="addContact">添加基础信息字段</el-button>
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
        <el-input
          class="section-title"
          :model-value="section.title"
          :maxlength="LIMITS.sectionTitle"
          aria-label="章节标题"
          @update:model-value="
            (value: string) => updateSection(section.id, (target) => (target.title = value))
          "
        />
        <div class="section-actions">
          <button
            class="section-drag-handle"
            type="button"
            draggable="true"
            aria-label="拖拽调整章节顺序"
            @dragstart="startSectionDrag(section.id, $event)"
            @dragend="draggedSectionId = null"
          >
            拖拽排序
          </button>
          <el-button
            size="small"
            :disabled="sectionIndex === 0"
            aria-label="上移章节"
            @click="moveSection(sectionIndex, -1)"
          >
            上移
          </el-button>
          <el-button
            size="small"
            :disabled="sectionIndex === document.sections.length - 1"
            aria-label="下移章节"
            @click="moveSection(sectionIndex, 1)"
          >
            下移
          </el-button>
        </div>
      </header>

      <div v-if="section.entries.length === 0" class="editor-empty">
        该章节暂无内容，可以添加条目。
      </div>

      <article v-for="entry in section.entries" :key="entry.id" class="editor-entry">
        <div class="entry-line">
          <el-input
            class="entry-heading"
            :model-value="entry.heading ?? ''"
            :maxlength="LIMITS.entryField"
            placeholder="标题，如公司、学校或项目名"
            @update:model-value="
              (value: string) => updateEntry(section.id, entry.id, (target) => (target.heading = value))
            "
          />
          <el-input
            class="entry-meta"
            :model-value="entry.meta ?? ''"
            :maxlength="LIMITS.entryField"
            placeholder="时间 / 职位等"
            @update:model-value="
              (value: string) => updateEntry(section.id, entry.id, (target) => (target.meta = value))
            "
          />
          <el-button
            text
            type="danger"
            aria-label="删除条目"
            @click="
              updateSection(section.id, (target) => {
                target.entries = target.entries.filter((item) => item.id !== entry.id)
              })
            "
          >
            删除条目
          </el-button>
        </div>

        <div
          v-for="bullet in entry.bullets"
          :key="bullet.id"
          class="bullet-block"
        >
          <div class="bullet-line">
            <el-input
              type="textarea"
              :autosize="{ minRows: 1, maxRows: 6 }"
              :model-value="bullet.text"
              :maxlength="LIMITS.bullet"
              placeholder="要点内容"
              @update:model-value="
                (value: string) =>
                  updateEntry(section.id, entry.id, (target) => {
                    const targetBullet = target.bullets.find((item) => item.id === bullet.id)
                    if (targetBullet) targetBullet.text = value
                  })
              "
            />
            <div class="bullet-actions">
              <el-dropdown
                v-if="suggestEnabled && suggest"
                trigger="click"
                @command="(command: unknown) => handleSuggestCommand(bullet.id, command as BulletSuggestIntent | 'CUSTOM')"
              >
                <el-button
                  size="small"
                  :disabled="suggest.busy.value || suggestActive(bullet.id) || !bullet.text.trim()"
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
              <el-button
                text
                type="danger"
                aria-label="删除要点"
                @click="
                  updateEntry(section.id, entry.id, (target) => {
                    target.bullets = target.bullets.filter((item) => item.id !== bullet.id)
                  })
                "
              >
                删除
              </el-button>
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

        <div class="entry-actions">
          <el-button size="small" @click="addBullet(section.id, entry.id)">添加要点</el-button>
        </div>
      </article>

      <div class="section-footer">
        <el-button size="small" @click="addEntry(section.id)">添加条目</el-button>
      </div>
    </section>
  </div>
</template>

<style scoped>
.resume-editor {
  display: grid;
  gap: 18px;
}

.editor-block {
  border: 1px solid var(--el-border-color-light);
  border-radius: 10px;
  background: var(--el-bg-color);
  padding: 18px;
  display: grid;
  gap: 12px;
}

.editor-block-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.editor-block-header h3 {
  margin: 0;
  color: var(--app-navy);
  font-size: 16px;
}

.section-title {
  max-width: 320px;
  font-weight: 700;
}

.section-actions {
  display: flex;
  align-items: center;
  gap: 6px;
}

.section-drag-handle {
  border: 0;
  background: transparent;
  color: var(--app-text-secondary);
  cursor: grab;
  font: inherit;
  font-size: 12px;
  padding: 4px 6px;
}

.section-drag-handle:active {
  cursor: grabbing;
}

.basics-grid {
  display: grid;
  gap: 10px;
}

.editor-field {
  display: grid;
  gap: 4px;
}

.editor-field > span {
  color: var(--app-text-secondary);
  font-size: 12px;
  font-weight: 700;
}

.editor-field.is-inline {
  grid-template-columns: 140px minmax(0, 1fr) auto;
  align-items: center;
}

.editor-empty {
  color: var(--app-text-secondary);
  font-size: 13px;
}

.editor-entry {
  border-top: 1px dashed var(--el-border-color-lighter);
  padding-top: 12px;
  display: grid;
  gap: 8px;
}

.entry-line {
  display: grid;
  grid-template-columns: minmax(0, 1.2fr) minmax(0, 1fr) auto;
  gap: 8px;
  align-items: center;
}

.bullet-block {
  display: grid;
  gap: 8px;
}

.bullet-line {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 8px;
  align-items: start;
}

.bullet-actions {
  display: flex;
  align-items: center;
  gap: 4px;
}

.entry-actions,
.section-footer {
  display: flex;
}

@media (max-width: 720px) {
  .editor-field.is-inline,
  .entry-line {
    grid-template-columns: 1fr;
  }
}
</style>
