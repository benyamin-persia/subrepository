# Live Fact Check Overlay

This project is my attempt to build a phone-first fact-checking assistant that works while I am reading content inside X/Twitter on Android.

The goal is simple:

- detect the tweet or post I am currently looking at
- extract the visible text automatically
- check that text against live information from the internet
- show the result as a floating overlay on top of the screen

I wanted the experience to feel immediate and mobile-native, not like copying text into a browser or manually pasting claims into another tool.

## My perspective

I built this as an experiment around a very practical question:

> Can my phone help me challenge claims in real time while I am reading them?

The answer is: partially yes, but the hard part is not only the Android UI. The hard part is the fact-check pipeline itself.

The app can already do the device-side work:

- watch screen changes
- read visible text through Android accessibility
- fall back to OCR screenshots when accessibility text is incomplete
- show a floating overlay
- narrow the trigger to X/Twitter only

What remains difficult is the reasoning backend:

- current fact-checking needs live web retrieval
- live web retrieval needs a backend or search layer
- high-quality reasoning usually needs a model service or a local model runtime

So this repository captures both the product idea and the engineering reality behind it.

## What the app does today

### Android client

- Android app written in Kotlin
- floating overlay UI
- accessibility service for on-screen text extraction
- OCR fallback using ML Kit text recognition
- X/Twitter-only trigger logic
- configurable backend endpoint in app settings

### Backend

- lightweight Python HTTP server
- `/health` endpoint
- `/factcheck` endpoint
- currently structured for a live fact-checking backend call

## Why this project matters to me

Most social content is consumed in fast scroll mode. People rarely stop to verify a claim because verification has too much friction.

This project tries to reduce that friction.

Instead of:

- stop scrolling
- copy text
- open browser
- search manually
- compare sources

I want:

- keep reading
- detect claim
- inspect live evidence
- see a compact verdict immediately

That is the product vision behind this repository.

## Current limitations

This is still an experimental build.

- Android restricted settings may block accessibility permission for sideloaded apps
- not every app exposes text cleanly through accessibility
- OCR is slower than native text extraction
- live fact-checking still depends on a reachable backend with internet access
- this repository does not magically turn a CLI chat session into an app backend

## Architecture

### On-device

- `AccessibilityService` listens for screen changes
- visible text is extracted from the active window
- if the text is weak or incomplete, screenshot OCR is attempted
- text is sent to a backend endpoint
- overlay shows status, summary, and verdict

### Backend

- receives extracted text
- retrieves fresh information from the internet
- evaluates the claim
- returns structured JSON to the phone

## Repository structure

- `app/` Android application
- `backend/` local HTTP backend
- root Gradle files for Android build setup

## Status

This repository contains a working Android prototype build path that was assembled directly on-phone in Termux/proot.

The code is real, the app builds, and the concept is testable. The remaining work is mostly around:

- backend quality
- search/retrieval strategy
- permission UX
- better claim detection

## Intended next steps

- add stronger tweet/post detection heuristics
- support more local backend options
- improve trusted-source retrieval
- reduce overlay noise and false triggers
- support configurable app filters instead of hardcoded package names

## Final note

This project is not just about fact-checking. It is about reducing the distance between reading a claim and challenging it.

That is the core idea behind this codebase.
