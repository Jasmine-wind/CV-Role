// Classic v1 — built-in read-only template. Presentation only; all content comes from the
// generated data.typ where every user value is an escaped string literal.
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
  line(length: 100%, stroke: 1pt + accent)
}

#let section-title(title) = {
  v(0.7em)
  text(size: 12pt, weight: "bold", fill: accent, title)
  v(0.15em)
  line(length: 100%, stroke: 0.5pt + accent)
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
  for entry in section.entries {
    render-entry(entry)
  }
}
