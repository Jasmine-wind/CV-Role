// Modern v3 — built-in read-only template. Presentation only; all content comes from the
// generated data.typ where every user value is an escaped string literal.
#import "data.typ": resume

#let accent = rgb("#0f6e5d")
#let muted = rgb("#626a75")

#set page(paper: "a4", margin: (x: 1.8cm, y: 1.2cm))
#set text(
  font: "Noto Sans CJK SC",
  size: 10pt,
  weight: "regular",
  lang: "zh",
)
#set par(leading: 0.34em, justify: false)

#let basics = resume.basics
#let sections = resume.sections

#let education-contains(value) = {
  if value == "" {
    return false
  }
  for section in sections {
    if section.kind == "EDUCATION" {
      for entry in section.entries {
        if entry.degree == value {
          return true
        }
      }
    }
  }
  false
}

#let contact-item(contact) = {
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
    text(size: 22pt, weight: "bold", fill: accent, basics.name)
    v(0.16em)
  }
  if basics.job-intention != "" {
    text(size: 9.5pt, fill: muted, "求职意向：" + basics.job-intention)
    v(0.08em)
  }
  if basics.highest-education != "" and education-contains(basics.highest-education) == false {
    text(size: 9.5pt, fill: muted, "最高学历：" + basics.highest-education)
  }
  if basics.contacts.len() > 0 {
    v(0.18em)
    stack(
      dir: ttb,
      spacing: 0.1em,
      ..basics.contacts.map(c => text(size: 9pt, fill: muted, contact-item(c))),
    )
  }
  v(0.3em)
  line(length: 100%, stroke: 1.6pt + accent)
}

#let section-title(title) = {
  v(0.3em)
  block(
    inset: (left: 0.58em, y: 0.06em),
    stroke: (left: 2.6pt + accent),
    text(size: 12.5pt, weight: "bold", fill: accent, title),
  )
  v(0.14em)
}

#let compact-section-title(title) = {
  v(0.08em)
  block(
    inset: (left: 0.58em, y: 0.03em),
    stroke: (left: 2.6pt + accent),
    text(size: 11.5pt, weight: "bold", fill: accent, title),
  )
  v(0.06em)
}

#let render-entry-header(entry) = {
  let heading = entry-heading(entry)
  let dates = date-range(entry)
  if heading != "" and dates != "" {
    grid(
      columns: (1fr, auto),
      gutter: 0.9em,
      align: (left, right),
      text(weight: "bold", heading),
      text(size: 9pt, fill: muted, dates),
    )
  } else if heading != "" {
    text(weight: "bold", heading)
  } else if dates != "" {
    align(right, text(size: 9pt, fill: muted, dates))
  }
  let subline = entry-subline(entry)
  if subline != "" {
    v(0.04em)
    text(size: 9.5pt, fill: muted, subline)
  }
}

#let render-bullets(entry, start: 0, end: none) = {
  let end-index = if end == none { entry.bullets.len() } else { end }
  if end-index > start {
    v(0.04em)
    list(
      marker: rect(width: 0.28em, height: 0.28em, fill: accent),
      indent: 1.08em,
      body-indent: 0.34em,
      spacing: 0.42em,
      ..entry.bullets.slice(start, end-index),
    )
  }
}

#let render-plain-bullets(entry, start: 0, end: none) = {
  let end-index = if end == none { entry.bullets.len() } else { end }
  if end-index > start {
    for bullet in entry.bullets.slice(start, end-index) {
      if bullet != "" {
        text(bullet)
        linebreak()
      }
    }
  }
}

#let render-structured-entry(entry, with-bullets: true) = {
  let keep-first-content = entry.bullets.len() > 0 and entry.bullets.at(0).len() <= 600
  if keep-first-content {
    block(breakable: false, {
      render-entry-header(entry)
      if with-bullets {
        render-bullets(entry, end: 1)
      } else {
        render-plain-bullets(entry, end: 1)
      }
    })
    if entry.bullets.len() > 1 {
      if with-bullets {
        render-bullets(entry, start: 1)
      } else {
        render-plain-bullets(entry, start: 1)
      }
    }
  } else {
    block(breakable: false, render-entry-header(entry))
    if with-bullets {
      render-bullets(entry)
    } else {
      render-plain-bullets(entry)
    }
  }
  v(0.14em)
}

#let render-skill-entry(entry) = {
  let items = entry.skill-items.join("、")
  if items == "" {
    return
  }
  if entry.group != "" {
    text(size: 9.8pt, weight: "bold", entry.group + "：")
    text(size: 9.8pt, items)
  } else {
    text(size: 9.8pt, items)
  }
  linebreak()
}

#let render-generic-entry(entry) = {
  for bullet in entry.bullets {
    if bullet != "" {
      text(bullet)
      linebreak()
    }
  }
  v(0.18em)
}

#let render-generic-bullets(title, entry) = {
  let first-bullet = if entry.bullets.len() > 0 { entry.bullets.at(0) } else { "" }
  let keep-first-content = first-bullet != "" and first-bullet.len() <= 600
  if keep-first-content {
    block(breakable: false, {
      section-title(title)
      text(first-bullet)
      linebreak()
    })
    if entry.bullets.len() > 1 {
      for bullet in entry.bullets.slice(1) {
        if bullet != "" {
          text(bullet)
          linebreak()
        }
      }
    }
  } else {
    block(breakable: false, section-title(title))
    render-generic-entry(entry)
  }
}

#let render-generic-values(title, values) = {
  let first-value = values.at(0)
  let keep-first-content = first-value.len() <= 600
  if keep-first-content {
    block(breakable: false, {
      compact-section-title(title)
      text(first-value)
    })
    if values.len() > 1 {
      text(" · " + values.slice(1).join(" · "))
    }
  } else {
    block(breakable: false, compact-section-title(title))
    text(values.join(" · "))
  }
}

#let render-structured-section(section) = {
  let entries = section.entries
  let first = entries.at(0)
  let with-bullets = section.kind == "EXPERIENCE" or section.kind == "PROJECT"
  let keep-first-content = first.bullets.len() > 0 and first.bullets.at(0).len() <= 600
  if keep-first-content {
    block(breakable: false, {
      section-title(section.title)
      render-entry-header(first)
      if with-bullets {
        render-bullets(first, end: 1)
      } else {
        render-plain-bullets(first, end: 1)
      }
    })
    if first.bullets.len() > 1 {
      if with-bullets {
        render-bullets(first, start: 1)
      } else {
        render-plain-bullets(first, start: 1)
      }
    }
  } else {
    block(breakable: false, {
      section-title(section.title)
      render-entry-header(first)
    })
    if with-bullets {
      render-bullets(first)
    } else {
      render-plain-bullets(first)
    }
  }
  v(0.14em)
  if entries.len() > 1 {
    for entry in entries.slice(1) {
      render-structured-entry(entry, with-bullets: with-bullets)
    }
  }
}

#let render-skill-section(section) = {
  let entries = section.entries
  let first = entries.at(0)
  block(breakable: false, {
    section-title(section.title)
    render-skill-entry(first)
  })
  if entries.len() > 1 {
    for entry in entries.slice(1) {
      render-skill-entry(entry)
    }
  }
  v(0.1em)
}

#let render-generic-section(section) = {
  let entries = section.entries
  if section.kind == "CERTIFICATE" or section.kind == "ACHIEVEMENT" {
    let values = ()
    for entry in entries {
      for bullet in entry.bullets {
        if bullet != "" {
          values.push(bullet)
        }
      }
    }
    if values.len() > 0 {
      render-generic-values(section.title, values)
    }
  } else {
    let first = entries.at(0)
    render-generic-bullets(section.title, first)
    if entries.len() > 1 {
      for entry in entries.slice(1) {
        render-generic-entry(entry)
      }
    }
  }
  v(0.08em)
}

#let render-section(section) = {
  if section.entries.len() == 0 {
    return
  }
  if section.kind == "SKILL" {
    render-skill-section(section)
  } else if section.kind == "EXPERIENCE" or section.kind == "PROJECT" or section.kind == "EDUCATION" {
    render-structured-section(section)
  } else {
    render-generic-section(section)
  }
}

#render-header()
#for section in sections {
  render-section(section)
}
