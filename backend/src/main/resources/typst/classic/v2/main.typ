// Classic v2 — built-in read-only template. Presentation only; all content comes from the
// generated data.typ where every user value is an escaped string literal.
// Slice A: consumes the semantic RESUME_DOCUMENT_V1 model and branches on section kind.
#import "data.typ": resume

#let accent = rgb("#1f3a5f")
#let muted = rgb("#5c6470")

#set page(paper: "a4", margin: (x: 2cm, y: 1.8cm))
#set text(
  font: ("Noto Sans CJK SC", "FandolHei", "Noto Sans", "DejaVu Sans"),
  size: 10pt,
  lang: "zh",
)
#set par(leading: 0.6em, justify: true)

#let basics = resume.basics
#let sections = resume.sections

#let contact-line(contact) = {
  if contact.label != "" {
    contact.label + ": " + contact.value
  } else {
    contact.value
  }
}

#let date-range(entry) = {
  let parts = ()
  if entry.start-date != "" {
    if entry.end-date != "" {
      parts.push(entry.start-date + " - " + entry.end-date)
    } else {
      parts.push(entry.start-date)
    }
  }
  if entry.location != "" {
    parts.push(entry.location)
  }
  parts.join(" · ")
}

#let entry-heading(entry) = {
  if entry.organization != "" {
    entry.organization
  } else if entry.school != "" {
    entry.school
  } else {
    ""
  }
}

#let entry-subline(entry) = {
  let parts = ()
  if entry.role != "" {
    parts.push(entry.role)
  }
  if entry.degree != "" {
    parts.push(entry.degree)
  }
  if entry.major != "" {
    parts.push(entry.major)
  }
  parts.join(" · ")
}

#let render-header() = {
  if basics.name != "" {
    align(center, text(size: 20pt, weight: "bold", basics.name))
    v(0.35em)
  }
  if basics.contacts.len() > 0 {
    align(center, text(
      size: 9.5pt,
      fill: muted,
      basics.contacts.map(contact-line).join("   |   "),
    ))
    v(0.3em)
  }
  if basics.job-intention != "" or basics.highest-education != "" {
    let meta-parts = ()
    if basics.job-intention != "" {
      meta-parts.push("求职意向：" + basics.job-intention)
    }
    if basics.highest-education != "" {
      meta-parts.push("最高学历：" + basics.highest-education)
    }
    align(center, text(size: 9.5pt, fill: muted, meta-parts.join("   |   ")))
    v(0.3em)
  }
  line(length: 100%, stroke: 1pt + accent)
}

#let section-title(title) = {
  v(0.7em)
  text(size: 12pt, weight: "bold", fill: accent, title)
  v(0.15em)
  line(length: 100%, stroke: 0.5pt + accent)
  v(0.4em)
}

#let render-structured-entry(entry) = {
  let heading = entry-heading(entry)
  let dates = date-range(entry)
  if heading != "" or dates != "" {
    grid(
      columns: (1fr, auto),
      align: (left, right),
      strong(heading),
      text(size: 9pt, fill: muted, dates),
    )
  }
  let subline = entry-subline(entry)
  if subline != "" {
    v(0.05em)
    text(size: 9.5pt, fill: muted, subline)
  }
  if entry.bullets.len() > 0 {
    v(0.1em)
    list(
      marker: text(fill: accent)[•],
      spacing: 0.35em,
      ..entry.bullets,
    )
  }
  v(0.3em)
}

#let render-skill-entry(entry) = {
  let items = entry.skill-items.join("、")
  if items == "" {
    return
  }
  if entry.group != "" {
    [#strong(entry.group)：#items]
  } else {
    items
  }
  linebreak()
}

#let render-generic-entry(entry) = {
  if entry.bullets.len() > 0 {
    list(
      marker: text(fill: accent)[•],
      spacing: 0.35em,
      ..entry.bullets,
    )
  }
  v(0.3em)
}

#render-header()
#for section in sections {
  section-title(section.title)
  if section.kind == "SKILL" {
    set par(leading: 0.8em)
    for entry in section.entries {
      render-skill-entry(entry)
    }
  } else if section.kind == "EXPERIENCE" or section.kind == "PROJECT" or section.kind == "EDUCATION" {
    for entry in section.entries {
      render-structured-entry(entry)
    }
  } else {
    for entry in section.entries {
      render-generic-entry(entry)
    }
  }
}
