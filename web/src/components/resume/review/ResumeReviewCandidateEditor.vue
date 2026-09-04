<script setup lang="ts">
import { nextTick, reactive, watch } from 'vue'
import type { ReviewItemState } from '@/views/resume/resumeReviewPresentation'
import {
  entryDraftSummary,
  getReviewCandidatePresentation,
  getReviewEntryTitle,
} from '@/views/resume/resumeReviewPresentation'

const props = defineProps<{
  state: ReviewItemState
  reviewName: string
  reviewNameMissing: boolean
  contactTypeOptions: Array<{ value: string; label: string }>
  requiredContactTypeOptions: Array<{ value: string; label: string }>
}>()

const emit = defineEmits<{
  'update:reviewName': [value: string]
  'update:state': [state: ReviewItemState]
}>()

const cloneState = (state: ReviewItemState): ReviewItemState =>
  JSON.parse(JSON.stringify(state)) as ReviewItemState
const draft = reactive(cloneState(props.state))
let syncingFromParent = false

watch(
  () => props.state.item.id,
  async () => {
    syncingFromParent = true
    Object.assign(draft, cloneState(props.state))
    await nextTick()
    syncingFromParent = false
  },
)
watch(
  draft,
  () => {
    if (!syncingFromParent) emit('update:state', cloneState(draft))
  },
  { deep: true },
)

const candidate = () => getReviewCandidatePresentation(draft)
const id = () => draft.item.id
const entryOrganizationLabel = () => {
  if (draft.entry.kind === 'EDUCATION') return '学校'
  return draft.entry.kind === 'PROJECT' ? '项目名' : '公司'
}
const entryOrganizationPlaceholder = () => {
  if (draft.entry.kind === 'EDUCATION') return '请填写学校'
  return draft.entry.kind === 'PROJECT' ? '请填写项目名' : '请填写公司'
}
const entryRoleLabel = () => draft.entry.kind === 'PROJECT' ? '角色' : '职位'
const entryRolePlaceholder = () => draft.entry.kind === 'PROJECT' ? '可选角色' : '可选职位'
const entryBulletLabel = () => {
  if (draft.entry.kind === 'PROJECT') return '项目要点'
  if (draft.entry.kind === 'EXPERIENCE') return '工作要点'
  return '内容'
}
</script>

<template>
  <section class="resume-review-candidate" :aria-labelledby="`resume-candidate-title-${id()}`">
    <header class="resume-review-candidate-header">
      <div>
        <span class="resume-review-candidate-kicker">当前候选</span>
        <h3 :id="`resume-candidate-title-${id()}`">{{ candidate().title }}</h3>
      </div>
      <span class="resume-review-candidate-kind">
        {{ draft.item.kind === 'ENTRY_CANDIDATE' ? getReviewEntryTitle(draft.entry.kind) : candidate().title }}
      </span>
    </header>

    <p class="resume-review-reason">{{ candidate().description }}</p>

    <div v-if="props.reviewNameMissing" class="resume-review-name">
      <label :for="`resume-review-name-${id()}`">姓名</label>
      <input
        :id="`resume-review-name-${id()}`"
        :value="props.reviewName"
        placeholder="请输入姓名"
        @input="emit('update:reviewName', ($event.target as HTMLInputElement).value)"
      />
      <small>姓名会随本次确认一并保存。</small>
    </div>

    <div v-if="draft.item.kind === 'CONTACT_CANDIDATE' || draft.item.kind === 'REQUIRED_CONTACT_CANDIDATE'" class="resume-review-form-grid">
      <label :for="`resume-contact-type-${id()}`">联系方式类型</label>
      <select :id="`resume-contact-type-${id()}`" v-model="draft.contact.type">
        <option
          v-for="option in draft.item.kind === 'REQUIRED_CONTACT_CANDIDATE' ? props.requiredContactTypeOptions : props.contactTypeOptions"
          :key="option.value"
          :value="option.value"
        >
          {{ option.label }}
        </option>
      </select>
      <label :for="`resume-contact-value-${id()}`">联系方式内容</label>
      <input
        :id="`resume-contact-value-${id()}`"
        v-model="draft.contact.value"
        placeholder="请输入电话、邮箱或其他联系方式"
      />
    </div>

    <div v-else-if="draft.item.kind === 'NAME_CANDIDATE'" class="resume-review-form-grid">
      <label :for="`resume-candidate-name-${id()}`">姓名</label>
      <input :id="`resume-candidate-name-${id()}`" v-model="draft.text" placeholder="请核对姓名" />
    </div>

    <div v-else-if="draft.item.kind === 'TEXT_FRAGMENT'" class="resume-review-form-grid">
      <label :for="`resume-fragment-${id()}`">内容</label>
      <textarea :id="`resume-fragment-${id()}`" v-model="draft.text" rows="5" placeholder="请核对这段内容"></textarea>
    </div>

    <div v-else-if="draft.item.kind === 'ENTRY_CANDIDATE'" class="resume-review-entry-form">
      <div class="resume-review-field-grid">
        <label :for="draft.entry.kind === 'EDUCATION' ? `resume-school-${id()}` : `resume-organization-${id()}`">{{ entryOrganizationLabel() }}</label>
        <input v-if="draft.entry.kind === 'EDUCATION'" :id="`resume-school-${id()}`" v-model="draft.entry.school" :placeholder="entryOrganizationPlaceholder()" />
        <input v-else :id="`resume-organization-${id()}`" v-model="draft.entry.organization" :placeholder="entryOrganizationPlaceholder()" />
      </div>
      <div class="resume-review-field-grid">
        <label :for="`resume-role-${id()}`">{{ entryRoleLabel() }}</label>
        <input :id="`resume-role-${id()}`" v-model="draft.entry.role" :placeholder="entryRolePlaceholder()" />
      </div>
      <div v-if="draft.entry.kind === 'EDUCATION'" class="resume-review-field-pair">
        <div class="resume-review-field-grid">
          <label :for="`resume-degree-${id()}`">学历</label>
          <input :id="`resume-degree-${id()}`" v-model="draft.entry.degree" placeholder="可选" />
        </div>
        <div class="resume-review-field-grid">
          <label :for="`resume-major-${id()}`">专业</label>
          <input :id="`resume-major-${id()}`" v-model="draft.entry.major" placeholder="可选" />
        </div>
      </div>
      <div class="resume-review-field-pair">
        <div class="resume-review-field-grid">
          <label :for="`resume-start-${id()}`">开始时间</label>
          <input :id="`resume-start-${id()}`" v-model="draft.entry.startDate" placeholder="可选" />
        </div>
        <div class="resume-review-field-grid">
          <label :for="`resume-end-${id()}`">结束时间</label>
          <input :id="`resume-end-${id()}`" v-model="draft.entry.endDate" placeholder="可选" />
        </div>
      </div>
      <div v-for="(bullet, index) in draft.entry.bullets" :key="`${id()}-bullet-${index}`" class="resume-review-field-grid">
        <label :for="`resume-bullet-${id()}-${index}`">{{ entryBulletLabel() }} {{ index + 1 }}</label>
        <textarea :id="`resume-bullet-${id()}-${index}`" v-model="bullet.text" rows="3" :placeholder="`请核对${entryBulletLabel()}`"></textarea>
      </div>
    </div>

    <div v-else class="resume-review-entry-preview">
      <strong>{{ entryDraftSummary(draft.item.canonicalDraft).title }}</strong>
      <span v-if="entryDraftSummary(draft.item.canonicalDraft).details.length">
        {{ entryDraftSummary(draft.item.canonicalDraft).details.join(' · ') }}
      </span>
      <p v-for="bullet in entryDraftSummary(draft.item.canonicalDraft).bullets" :key="bullet">{{ bullet }}</p>
      <p v-if="!entryDraftSummary(draft.item.canonicalDraft).bullets.length">当前候选没有可编辑的结构化字段。</p>
    </div>
  </section>
</template>
