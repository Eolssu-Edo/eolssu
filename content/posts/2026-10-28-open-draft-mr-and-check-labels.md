---
title: "초안 MR의 라벨 확인하기"
date: 2026-10-28T00:00:00+09:00
slug: "open-draft-mr-and-check-labels"
author: "우동균"
categories: ["infra"]
tags: ["Git", "GitLab", "MR"]
description: "Git의 Push 옵션으로 초안 MR을 열고, GitLab에서 라벨과 설명이 어떻게 저장됐는지 확인한 기록입니다."
thumbnail: "https://team-edo.github.io/blog-images/2026-10-open-draft-mr-and-check-labels.webp"
draft: false
---

연습 기록지 요약 작업을 올릴 때, 브랜치를 Push하면서 GitLab에 초안 MR도 열었습니다. 검토를 받기 전에 채울 내용이 남아 있어 초안으로 표시했고, 어느 브랜치에 합칠지도 명령에 적었습니다. Push 응답의 `remote:` 줄에서 생성된 MR 주소를 얻었습니다.

```sh
git push -u origin HEAD \
  -o merge_request.create \
  -o merge_request.target=develop \
  -o merge_request.draft \
  -o 'merge_request.title=연습 기록지 요약 변경'
```

`HEAD`는 현재 작업 브랜치를 가리킵니다. `merge_request.create`로 MR을 열고 `target`과 `draft`로 대상과 초안 여부를 지정합니다. 생성 뒤 받은 링크를 열면 브랜치와 초안 표시를 직접 대조할 수 있습니다. [GitLab의 MR Push 옵션](https://docs.gitlab.com/topics/git/commit/#push-options-for-merge-requests)

## 라벨 두 개를 붙이고 재조회 {#라벨-두-개를-붙이고-다시-읽었다}

별도로 작업한 MR에서는 프로젝트에 이미 있는 라벨 두 개를 붙이기 위해 설명의 마지막 줄에 `/label`을 적었습니다. 다음 예시처럼 빠른 동작을 한 줄에 두고, 공백이 들어간 라벨 이름은 큰따옴표로 감쌉니다. [GitLab의 라벨 빠른 동작](https://docs.gitlab.com/user/project/quick_actions/#label)

```text
/label ~feature ~"needs review"
```

MR을 만든 뒤 API로 같은 MR을 다시 읽었습니다. `labels`에 지정한 **두 라벨**이 들어 있었고, 저장된 `description`에서는 `/label` 줄이 빠져 있었습니다. 프로젝트의 전체 라벨 수도 그대로여서, 이미 정의된 라벨이 붙었다는 점까지 확인했습니다. [GitLab의 MR 단건 조회](https://docs.gitlab.com/api/merge_requests/#retrieve-a-merge-request)
