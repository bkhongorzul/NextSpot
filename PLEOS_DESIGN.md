# Pleos Connect 디자인 기준 (NextSpot용 요약)

출처: Pleos Connect 공식 Figma 파일 `Pleos_Connect_design_assets_215.fig`, `Pleos_Connect_display_guide_215.fig`
(Figma 변수에서 직접 뽑은 값. 단위는 Connect-S 기준 dp/sp, 160dpi라 px와 같음)

**원칙: 우리 취향보다 Pleos 내장 UI와 같아 보이는 것이 우선.** 차 안의 다른 앱과 이질감이 없어야 한다.

## 1. 색 (Hyundai 테마, 다크 모드 기준 / 괄호는 라이트)

### 바탕과 면
| 토큰 | 다크 | 라이트 | 용도 |
|---|---|---|---|
| background/primary | `#1D1E21` | `#F7F8FA` | 앱 전체 배경 |
| background/secondary | `#131417` | `#FFFFFF` | 더 깊은 배경 (지도 바닥 등) |
| surface/basic | `#1D1E21` | `#FFFFFF` | 기본 카드 |
| surface/high | `#313236` | `#EDEEF2` | 강조 카드, 팝업 |
| surface/accent | `#44464E` | `#E0E1E6` | 선택된 면 |
| background/popup | `#313236` | `#FFFFFF` | 팝업 |

### 회색 단계 (basic)
| 단계 | 다크 | 라이트 |
|---|---|---|
| 00 | `#131417` | `#FFFFFF` |
| 50 | `#1D1E21` | `#F7F8FA` |
| 100 | `#313236` | `#EDEEF2` |
| 200 | `#44464E` | `#E0E1E6` |
| 300 | `#686A72` | `#C7C9CE` |
| 400 | `#A4A8AE` | `#A4A8AE` |
| 500 | `#C7C9CE` | `#686A72` |
| 600 | `#E0E1E6` | `#44464E` |
| 700 | `#EDEEF2` | `#313236` |
| 900 | `#FFFFFF` | `#131417` |

### 글자·아이콘 (다크: 흰색 기준 투명도 / 라이트: `#131417` 기준)
| 토큰 | 다크 | 용도 |
|---|---|---|
| text/primary | `#FFFFFF` 100% | 제목, 본문 |
| text/secondary | `#FFFFFF` 84% | 보조 본문 |
| text/tertiary | `#FFFFFF` 64% | 설명, 라벨 |
| text/quaternary | `#FFFFFF` 32% | 비활성, 힌트 |

### 구분선
| 토큰 | 다크 |
|---|---|
| divider/primary | 흰색 5% |
| divider/secondary | 흰색 10% |
| divider/tertiary | 흰색 20% |

### 버튼
| 토큰 | 다크 | 라이트 | 설명 |
|---|---|---|---|
| button/filled/enabled | `#E0E1E6` (글자 `#131417`) | `#44464E` (글자 흰색) | 주 버튼. **다크에서는 밝은 버튼에 어두운 글자** |
| button/filled/pressed | `#C7C9CE` | `#313236` | |
| button/basic/enabled | 흰색 5% | 검정 5% | 보조 버튼 (거의 투명한 면) |
| button/basic/pressed | 흰색 10% | 검정 10% | |

### 의미 색 (informative)
| 토큰 | 다크 | 라이트 | NextSpot에서 |
|---|---|---|---|
| informative/active (=switch on) | `#00E074` | `#02C265` | 자리 확정, 켜진 신호 |
| informative/positive | `#0082FF` | `#0064FF` | 안내, 경로 |
| informative/error (주황) | `#FFA202` | `#FFA202` | "곧 빌 것 같음" |
| regulation/red | `#FF4339` | `#F62E24` | 취소, 철회 |
| driving/positive | `#00BA13` | `#00BA13` | |
| brand primary (보라) | `#6A57FF` | `#5A46FA` | Pleos 브랜드/자율주행 전용 → **NextSpot에서는 쓰지 않음** |

## 2. 글자 (폰트: **Asta Sans**, 42dot 제작 오픈 폰트)
| 스타일 | 크기 / 줄간격 | 굵기 |
|---|---|---|
| Headline/Large | 60 / 80 | Bold~ExtraBold |
| Headline/Small | 48 / 64 | Bold |
| Title/Large | 40 / 52 | Regular·Bold |
| Title/Medium | 36 / 44 | Regular·Bold |
| Title/Small | 32 / 40 | Bold |
| Body/Large | 30 / 38 | Regular·Bold |
| Body/Medium | 28 / 36 | Bold |
| Body/Small | 26 / 34 | Regular·Bold |
| Label/Medium | 24 / 28 | Regular |
| Label/Small | 20 / 24 | Regular |

→ **차량 화면 최소 글자는 20sp, 본문은 26~30sp.** 지금 NextSpot의 11~18sp는 너무 작다.

## 3. 버튼 크기
| 크기 | 높이 | 최소 너비 | 좌우 여백 | 모서리 |
|---|---|---|---|---|
| Large | 100 | 140 | 40 | 32 |
| Medium | 80 | 120 | 32 | 24 |
| Small | 64 | 100 | 24 | 16 |
| 아이콘 버튼 | 80×80 / 64×64 | | | 20 / 16 (원형은 999) |

## 4. 모서리·그림자
- 작은 요소 12, 일반 카드 16, 리스트 묶음 24~32, 시스템 팝업 48, 토스트 16
- 그림자: level1 = y6 blur24 검정 8% / level2 = y8 blur32 + y2 blur2 (검정 16%)

## 5. 화면 배치 (Window Policy)
- Connect-S에서 앱 2개면 화면 **1:1 분할이 기본**, 기본 Primary는 내비게이션.
- 즉 NextSpot은 실제로 **화면 절반 크기(세로로 긴 모양)**로 뜰 가능성이 크다. 절반 크기에서도 깨지지 않게 만들 것.
