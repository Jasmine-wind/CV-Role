export const LANDING_DEMO_STAGES = [
  {
    id: 'resume-job',
    index: '01',
    label: '材料对齐',
    title: '先把已有材料和目标岗位放在一起。',
    description:
      '目标岗位附着到当前简历，正文保持原样。这是一次具体的岗位定向，而不是重新写一份简历。',
    note: '同一份材料，先和一个具体岗位相遇',
  },
  {
    id: 'requirement',
    index: '02',
    label: '拆解要求',
    title: '把岗位要求，拆成可以核对的事项。',
    description: '从岗位内容里提取可核对的能力、任务和经验要求，让判断有明确对象。',
    note: '先确定要求，再判断材料',
  },
  {
    id: 'evidence',
    index: '03',
    label: '回到证据',
    title: '回到简历原文，看证据在哪里。',
    description: '同一条经历会被标记为真实证据。系统引用当前材料，不凭空猜测你会什么。',
    note: '每一条引用，都能回到当前简历原文',
  },
  {
    id: 'gap',
    index: '04',
    label: '生成建议',
    title: '有依据才改，没依据不乱写。',
    description:
      '只有简历中存在真实材料时，才提出更符合岗位重点的表达；缺少证据的能力不自动添加。',
    note: '缺口描述材料，不替你判断现实能力',
  },
  {
    id: 'confirm-export',
    index: '05',
    label: '确认导出',
    title: '你确认后，修改才真正写回简历。',
    description: '接受、保留或撤回建议。只有你确认过的内容，才会成为这次投递的岗位版本。',
    note: '确认写回，才可以导出 PDF',
  },
] as const

export type LandingDemoStageId = (typeof LANDING_DEMO_STAGES)[number]['id']

export const landingStageIndex = (stage: LandingDemoStageId) =>
  LANDING_DEMO_STAGES.findIndex((item) => item.id === stage)
