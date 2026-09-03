import type { ResumeReviewUnresolvedItem } from '@/api/resume'
import type { ResumeDocumentBullet } from '@/types/resume-document'

export interface ReviewDraftContact {
  type?: string
  label?: string | null
  value?: string
}

export interface ReviewDraftFragment {
  text?: string
}

export interface ReviewDraftEntry {
  kind?: string
  organization?: string | null
  role?: string | null
  school?: string | null
  degree?: string | null
  major?: string | null
  startDate?: string | null
  endDate?: string | null
  group?: string | null
  skillItems?: string[] | null
  bullets: ResumeDocumentBullet[]
}

export interface ReviewItemState {
  item: ResumeReviewUnresolvedItem
  contact: ReviewDraftContact
  entry: ReviewDraftEntry
  text: string
}

export interface ReviewCandidatePresentation {
  title: string
  description: string
  primaryAction: string
  canDelete: boolean
}

const parseDraft = <T,>(draft: string): T => {
  try {
    return JSON.parse(draft) as T
  } catch {
    return {} as T
  }
}

export const entryDraftSummary = (draft: string) => {
  const entry = parseDraft<ReviewDraftEntry>(draft)
  const title = entry.organization || entry.school || entry.group || '待确认条目'
  const details = [
    entry.role,
    entry.degree,
    entry.major,
    [entry.startDate, entry.endDate].filter(Boolean).join(' - '),
    entry.skillItems?.filter(Boolean).join('、'),
  ].filter((value): value is string => Boolean(value && value.trim()))
  const bullets = (entry.bullets ?? [])
    .map((bullet) => bullet.text?.trim())
    .filter((value): value is string => Boolean(value))
  return { title, details, bullets }
}

export const getReviewCandidatePresentation = (
  state: ReviewItemState,
): ReviewCandidatePresentation => {
  switch (state.item.kind) {
    case 'NAME_CANDIDATE':
      return {
        title: '姓名',
        description: state.item.reason || '系统无法安全确定哪段文字是姓名，请你核对。',
        primaryAction: '使用这个姓名',
        canDelete: false,
      }
    case 'REQUIRED_CONTACT_CANDIDATE':
      return {
        title: '必要联系方式',
        description: state.item.reason || '岗位投递通常需要至少一种可联系你的方式，请确认以下内容。',
        primaryAction: '保存这项联系方式',
        canDelete: false,
      }
    case 'CONTACT_CANDIDATE':
      return {
        title: '联系方式',
        description: state.item.reason || '请确认这项联系方式是否应该保留在简历中。',
        primaryAction: '保留这项联系方式',
        canDelete: true,
      }
    case 'TEXT_FRAGMENT':
      return {
        title: '未归类内容',
        description: state.item.reason || '系统读取到了这段文字，但无法安全判断它属于简历的哪个部分。',
        primaryAction: '保留这段内容',
        canDelete: true,
      }
    case 'ENTRY_CANDIDATE': {
      const title = getReviewEntryTitle(state.entry.kind)
      return {
        title,
        description: state.item.reason || '请核对这段经历的归属和内容。',
        primaryAction: title === '教育经历'
          ? '保留这段教育经历'
          : title === '工作经历'
            ? '保留这段工作经历'
            : title === '项目经历'
              ? '保留这段项目经历'
              : '保留这段经历',
        canDelete: true,
      }
    }
    default:
      return {
        title: '待确认内容',
        description: state.item.reason || '系统无法安全判断这项内容，请你核对。',
        primaryAction: '保留这项内容',
        canDelete: true,
      }
  }
}

export const getReviewEntryTitle = (kind?: string | null) => {
  switch (kind) {
    case 'EDUCATION':
      return '教育经历'
    case 'EXPERIENCE':
      return '工作经历'
    case 'PROJECT':
      return '项目经历'
    default:
      return '经历条目'
  }
}

export const getInitialReviewItemId = (items: ReviewItemState[]) =>
  items[0]?.item.id ?? null

export const selectReviewItemAfterResolve = ({
  previousItems,
  nextItems,
  resolvedItemId,
  previousActiveItemId,
}: {
  previousItems: ReviewItemState[]
  nextItems: ReviewItemState[]
  resolvedItemId: string
  previousActiveItemId: string | null
}) => {
  if (nextItems.length === 0) return null
  const sameItem = nextItems.find((state) => state.item.id === previousActiveItemId)
  if (sameItem) return sameItem.item.id

  const resolvedIndex = previousItems.findIndex((state) => state.item.id === resolvedItemId)
  const nextIndex = Math.min(Math.max(resolvedIndex, 0), nextItems.length - 1)
  return nextItems[nextIndex]?.item.id ?? null
}

export const getReviewProgressLabel = (index: number, total: number) =>
  `第 ${index + 1} 项 / 共 ${total} 项`
