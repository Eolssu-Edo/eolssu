---
title: "1.4.2 종료 코드는 0, 리뷰어는 빈칸"
date: 2026-11-03T14:46:20+09:00
slug: "coco-1-4-2-pr-metadata-readback"
author: "우동균"
categories: [ai-native]
tags: ["Skill", GitHub, GitLab, 코드리뷰]
description: "PR·MR에 담당자, 리뷰어, 라벨을 넣는 작업에서 명령의 종료 코드와 저장된 결과를 따로 확인했습니다."
thumbnail: "https://team-edo.github.io/blog-images/2026-11-coco-1-4-2-pr-metadata-readback.webp"
draft: false
aliases:
  - "/posts/2026/10/open-draft-mr-and-check-labels/"
---

새 PR에 리뷰어를 붙이는 규칙을 확인하던 중, `gh pr edit --add-reviewer`로 PR 작성자 자신에게 리뷰를 요청했을 때 종료 코드가 `0`으로 나왔습니다. 같은 요청을 GitHub API로 보내자 작성자에게 리뷰를 요청할 수 없다는 `422` 응답이 돌아왔고, PR을 다시 조회하니 리뷰어는 비어 있었습니다. 이 차이 때문에 1.4.2에서는 설정 명령이 끝난 뒤 담당자·리뷰어·라벨을 다시 읽어 반영 결과를 판단하게 했습니다. [수정 커밋](https://github.com/thisis-joe/skills/commit/7feeb2bafff91985702a574792ff10be082b2287)

## PR 생성과 메타데이터 설정 분리 {#pr-생성과-메타데이터-쓰기를-분리한다}

`coco-push`, `coco-merge`, `coco-ship`이 새 PR이나 MR을 생성하면 담당자와 리뷰어의 기본 후보는 해당 요청을 연 계정입니다. 로컬 Git 커밋 작성자 대신 GitHub나 GitLab에 로그인한 계정을 조회합니다. 사용자나 저장소 지침이 다른 사람을 지정했다면 그 지시를 따릅니다. 라벨은 저장소에 이미 있는 이름과 설명을 읽어 변경에 맞는 것만 추가합니다. 이미 열려 있던 요청은 생성 시점의 자동 설정 대상에서 제외합니다.

PR이나 MR이 만들어지면 그 요청을 대상으로 메타데이터를 따로 씁니다. 리뷰어 설정이 거절돼도 본문과 변경 범위가 담긴 요청은 이미 열려 있으므로, 그 요청을 다시 생성할 필요가 없습니다. 생성자가 리뷰어가 될 수 없는 경우에는 적용되지 않은 이유를 보고합니다. 다른 사람을 대신 지정할지는 사용자나 저장소 정책이 정할 일입니다.

## 다시 조회해 반영 확인 {#보고하는-값은-다시-조회해서-얻는다}

쓰기 명령의 성공은 명령 실행에 대한 정보입니다. 담당자나 라벨이 실제로 추가됐는지는 저장된 요청을 조회해야 알 수 있습니다. 그래서 COCO는 설정 요청 이후 담당자·리뷰어·라벨을 다시 읽고, 확인한 값과 반영에 실패한 항목을 나눠 보고합니다. 이 구조라면 담당자와 라벨은 반영되고 리뷰 요청만 거절됐을 때도, 사용자가 어떤 작업을 이어서 처리해야 하는지 바로 알 수 있습니다. [PR·MR 메타데이터 규칙](https://github.com/thisis-joe/skills/blob/v1.4.2/skills/_coco-shared/request-metadata.md)

## GitLab에서는 초안과 라벨을 따로 확인했다 {#라벨-두-개를-붙이고-다시-읽었다}

얼쑤에서는 작업 브랜치를 올리면서 GitLab 초안 MR을 연 사례도 있었습니다. Git Push 옵션 `merge_request.create`로 MR 생성을 요청하고, `merge_request.target=develop`과 `merge_request.draft`로 대상 브랜치와 초안 여부를 정했습니다. Push 응답의 `remote:` 줄에서 생성 주소를 얻었습니다. 이 주소에서 확인하는 것은 요청이 열렸는지와 실제 대상·초안 상태입니다. [GitLab의 MR Push 옵션](https://docs.gitlab.com/topics/git/commit/#push-options-for-merge-requests)

별도 MR에서는 설명 마지막 줄에 `/label ~feature ~"needs review"`처럼 기존 라벨을 지정하는 빠른 동작을 넣었습니다. 이때 공백이 있는 라벨 이름은 따옴표로 감쌉니다. 생성 뒤 같은 MR을 API로 읽으니 라벨 두 개가 붙어 있었고, 저장된 설명에는 `/label` 줄이 빠져 있었습니다. 프로젝트의 전체 라벨 수도 그대로여서 기존 라벨을 재사용했음을 확인했습니다. GitHub에서는 종료 코드가 0이어도 리뷰어가 비어 있었고, 이 GitLab 사례에서는 빠른 동작을 적은 줄이 사라졌어도 라벨은 정상 저장됐습니다. 두 경우 모두 최종 판정은 같은 요청을 다시 읽어서 내렸습니다. [GitLab 라벨 빠른 동작](https://docs.gitlab.com/user/project/quick_actions/#label) · [GitLab MR 단건 조회](https://docs.gitlab.com/api/merge_requests/#retrieve-a-merge-request)
