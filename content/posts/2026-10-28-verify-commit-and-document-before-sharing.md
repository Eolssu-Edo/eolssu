---
title: "공유 전에 커밋을 고치고, 공유할 문서의 버전을 확인하기"
date: 2026-10-28T00:00:00+09:00
slug: "verify-commit-and-document-before-sharing"
author: "우동균"
categories: ["infra"]
tags: ["Git", "코드 리뷰", "협업"]
description: "공유 전 커밋에서 관계없는 작업 키를 정리하고, 문서 링크가 병합된 제안을 가리키도록 확인한 두 사례입니다."
thumbnail: "https://team-edo.github.io/blog-images/2026-10-verify-commit-and-document-before-sharing.webp"
draft: false
---

얼쑤에서 분석 실패 처리의 리뷰를 반영한 뒤, 마지막 커밋 메시지에 다른 작업의 키가 들어 있는 것을 발견했습니다. 작업 키를 보면 리뷰어는 어느 작업을 변경했는지 판단합니다. 아직 Push하기 전이라 `git commit --amend`로 메시지를 다시 썼고, 저장된 커밋에는 이번 작업의 키만 남겼습니다.

## Push 전에 마지막 커밋을 읽는다

`--amend`는 마지막 커밋을 새 커밋으로 바꿉니다. 메시지만 고칠 때는 `--only`를 함께 써서 스테이징에 있는 다른 변경이 섞이지 않게 할 수 있습니다. 이후 `git log -1`로 새 커밋의 해시와 메시지를 확인합니다. [Git 커밋 문서](https://git-scm.com/docs/git-commit#Documentation/git-commit.txt---amend)

```sh
git log -1
git commit --amend --only
git log -1
```

## 문서의 파일명과 버전을 함께 확인한다

또 다른 작업에서는 연습 구간에 쓰는 음표 번호를 제안했습니다. 쉼표는 세지 않고 실제 음표만 0부터 세는 규칙이었습니다. 팀에 안내문을 쓰다 보니 제안이 로컬에만 있었습니다. 같은 이름의 문서가 기준 브랜치에 있어도 그 링크로는 새 문단을 읽을 수 없었습니다. 제안을 커밋하고 MR로 병합한 뒤, 기준 브랜치의 문서 위치와 절을 가리키도록 안내문을 다시 썼습니다.

문서의 현재 내용을 공유할 때는 원격 브랜치를 가져온 뒤 `git show`로 해당 버전의 본문을 읽을 수 있습니다. 아래는 예시 경로입니다. 검토를 부탁할 때는 변경이 보이는 MR 링크를, 병합된 규칙을 안내할 때는 해당 문단이 들어간 문서 링크를 사용합니다. [Git의 리비전과 파일 경로 표기](https://git-scm.com/docs/gitrevisions)

```sh
git fetch origin && git show 'origin/develop:docs/note-numbering.md'
```
