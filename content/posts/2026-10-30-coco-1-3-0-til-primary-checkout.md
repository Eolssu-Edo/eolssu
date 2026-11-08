---
title: "1.3.0 작업 폴더를 지워도 남는 TIL"
date: 2026-10-30T09:12:48+09:00
slug: "coco-1-3-0-til-primary-checkout"
author: "우동균"
categories: [ai-native]
tags: ["Skill", TIL, Git]
description: "세션에서 배운 내용을 개념별로 기록하고, linked worktree의 수명과 TIL의 저장 위치를 분리했습니다."
thumbnail: "https://team-edo.github.io/blog-images/2026-10-coco-1-3-0-til-primary-checkout.webp"
draft: false
---

기존 `coco-learn`은 실행한 worktree 아래에 노트를 저장했습니다. 작업이 끝난 worktree를 정리하면 Git이 추적하지 않는 개인 TIL도 함께 사라질 수 있는 구조였습니다. 1.3.0에서는 작업 폴더의 수명과 학습 기록의 수명을 분리했습니다. 어느 worktree에서 `coco-til`을 실행하든 기본 체크아웃에서 기록을 다시 찾을 수 있게 했습니다.

## 대화에서 다시 쓸 개념을 고른다

인자 없이 `coco-til`을 부르면 현재 대화에서 근거가 있고 다시 쓸 만한 개념을 고릅니다. 예를 들어 대화 중 같은 장애의 로그를 여러 차례 확인했더라도 기록할 내용은 명령 목록 전체보다 진단 범위를 좁히는 데 쓴 기준일 수 있습니다. 주제별로 최대 세 개를 고르고, 개념 하나를 노트 하나에 담습니다. 같은 개념을 이미 기록했다면 새로 확인한 근거가 있을 때 그 노트를 보완합니다. 같은 대화에서 다시 불렀을 때 새로운 내용이 없으면 본문과 날짜를 유지합니다. [COCO 1.3.0 릴리스 노트](https://github.com/thisis-joe/skills/blob/c94cfde/docs/coco-1.3.0.md)

## 저장할 폴더도 Git의 정보로 확인한다

기본 체크아웃의 이름이나 위치를 관례로 추측하면 여러 worktree를 쓰는 환경에서 빗나갈 수 있습니다. Git 메타데이터를 작업 폴더와 분리한 저장소에서는 메타데이터 디렉터리가 기본 작업 폴더처럼 보일 수 있었습니다. 이 경우 Git에 기록된 `core.worktree`를 확인하고, 후보 폴더가 현재 저장소의 기본 체크아웃인지 검증하도록 수정했습니다. 확인할 수 없다면 저장을 멈춥니다. 임시 작업 폴더에 새 TIL을 만들어 버리면 이후 작업에서 같은 문제를 다시 만날 수 있기 때문입니다. 기존 linked worktree에 있던 기록은 내용을 비교해 복사하고, 서로 다른 원본은 보존합니다.

격리한 실제 Git 저장소에서 기본 체크아웃과 linked worktree의 하위 폴더로부터 같은 TIL 위치를 찾는지 확인했습니다. 메타데이터가 분리된 구성에서는 절대·상대 경로를 모두 확인했고, linked worktree를 제거한 뒤에도 노트가 남았습니다. 이 검증은 임시 작업 폴더 정리에 대한 것이므로, 기본 체크아웃 자체를 지우거나 옮길 때의 데이터 보호는 별도로 다뤄야 합니다. [Git 저장소와 설치 검증 기록](https://github.com/thisis-joe/skills/blob/c94cfde/docs/evaluation/coco-til-capture.md)
