// Modern v1 — built-in read-only template. Presentation only; all content comes from the
// generated data.typ where every user value is an escaped string literal.
#import "data.typ": resume

#let accent = rgb("#0f6e5d")
#let muted = rgb("#626a75")

#set page(paper: "a4", margin: (x: 2cm, y: 1.8cm))
#set text(
  font: ("Noto Sans CJK SC", "FandolHei", "Noto Sans", "DejaVu Sans"),
  size: 10pt,
  lang: "zh",
)
#set par(leading: 0.6em, justify: true)

#let basics = resume.basics
#let sections = resume.sections

#let contact-item(contact) = {
  if contact.label != "" {
    contact.label + ": " + contact.value
  } else {
    contact.value
  }
}

#let render-header() = {
  grid(
    columns: (1fr, auto),
    align: (left, bottom),
    {
      if basics.name != "" {
        text(size: 22pt, weight: "bold", fill: accent, basics.name)
      }
    },
    {
      if basics.contacts.len() > 0 {
        align(right, stack(
          dir: ttb,
          spacing: 0.15em,
          ..basics.contacts.map(c => text(size: 9pt, fill: muted, contact-item(c))),
        ))
      }
    },
  )
  v(0.5em)
  line(length: 100%, stroke: 1.6pt + accent)
}

#let section-title(title) = {
  v(0.75em)
  block(
    inset: (left: 0.55em, y: 0.12em),
    stroke: (left: 2.6pt + accent),
    text(size: 12.5pt, weight: "bold", fill: accent, title),
  )
  v(0.4em)
}

#let render-entry(entry) = {
  if entry.heading != "" or entry.meta != "" {
    grid(
      columns: (1fr, auto),
      align: (left, right),
      strong(entry.heading),
      text(size: 9pt, fill: muted, entry.meta),
    )
    v(0.15em)
  }
  if entry.bullets.len() > 0 {
    list(
      marker: text(fill: accent)[▪],
      spacing: 0.35em,
      ..entry.bullets,
    )
  }
  v(0.3em)
}

#render-header()
#for section in sections {
  section-title(section.title)
  for entry in section.entries {
    render-entry(entry)
  }
}
