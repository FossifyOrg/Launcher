# GitHub Setup Instructions

All M1 files are committed locally. Follow these steps to push to GitHub.

---

## Option A: GitHub CLI (Fastest)

If you have `gh` CLI installed:

```bash
cd /path/to/workspace  # Where this repo is
gh repo create launchpad-m1 \
  --public \
  --source=. \
  --remote=origin \
  --push \
  --description "LAUNCHPAD M1: Fossify Launcher fork with Krypto-Cash, time budgeting, parental controls"
```

Done! Your repo is live.

---

## Option B: Create Repo on GitHub.com, Then Push

1. **Create new repo on GitHub.com**:
   - Go to https://github.com/new
   - Repository name: `launchpad-m1`
   - Description: "LAUNCHPAD M1: Fossify Launcher fork with Krypto-Cash, time budgeting, parental controls"
   - Public or Private (your choice)
   - **Do NOT** initialize with README, .gitignore, or license (we have these)
   - Click "Create repository"

2. **Add remote and push**:
   ```bash
   cd /path/to/workspace
   
   # Replace YOUR-USERNAME with your actual GitHub username
   git remote add origin https://github.com/YOUR-USERNAME/launchpad-m1.git
   git branch -M main
   git push -u origin main
   ```

3. **Verify**:
   - Visit https://github.com/YOUR-USERNAME/launchpad-m1
   - You should see all 18 files, organized structure, and comprehensive README

---

## Option C: Organization Repo

If creating under an org (e.g., `inkandironglow`):

1. Create on https://github.com/organizations/YOUR-ORG/repositories/new

2. Push:
   ```bash
   git remote add origin https://github.com/YOUR-ORG/launchpad-m1.git
   git branch -M main
   git push -u origin main
   ```

---

## Repository Contents (What's being pushed)

```
launchpad-m1/
├── README.md                                 ← Start here
├── .gitignore
├── docs/guides/
│   ├── 00_INTEGRATION_QUICKSTART.md         ← 12-step guide
│   ├── 15_m1_implementation_summary.md      ← Full spec
│   ├── VERIFICATION_CHECKLIST.md             ← Testing guide
│   └── MASTER_FILE_INDEX.txt                 ← File reference
└── impl/
    ├── models/          (KryptoCash data + tests)
    ├── database/        (Room entities & migration)
    ├── activities/      (Parent mode, cool-down)
    ├── fragments/       (Safe WebView)
    ├── helpers/         (Launch gate, filters, PIN)
    ├── crypto/          (QR pairing protocol)
    └── build/           (Gradle config, manifest)
```

---

## After Push: Next Steps

1. **Clone your new repo**:
   ```bash
   git clone https://github.com/YOUR-USERNAME/launchpad-m1.git
   cd launchpad-m1
   ```

2. **Fork Fossify Launcher separately**:
   ```bash
   git clone https://github.com/fossifyorg/Launcher.git my-fossify-fork
   cd my-fossify-fork
   ```

3. **Follow integration guide**:
   ```bash
   cat ../launchpad-m1/docs/guides/00_INTEGRATION_QUICKSTART.md
   ```

4. **Copy files per MASTER_FILE_INDEX.txt**:
   - impl/models → :shared/src/main/java/org/fossify/launchpad/models/
   - impl/database → app/src/main/java/org/fossify/home/databases/
   - (etc., per guide)

5. **Build & test**:
   ```bash
   ./gradlew clean build
   ./gradlew :shared:test    # 8 tests pass ✓
   ```

---

## Repository Features to Enable (Optional)

Once pushed, on GitHub.com:

1. **Discussions** (for questions):
   - Settings → Features → Enable Discussions

2. **Projects** (for tracking M2-M5):
   - Click "Projects" tab
   - Create "M1 Integration" project (track blockers)
   - Create "M2 Roadmap" project

3. **Wiki** (optional):
   - Would be redundant with docs/guides/, skip

4. **Branch Protection** (optional):
   - If team working on it, require reviews

---

## Sharing the Link

Once live, you can share:

```
GitHub: https://github.com/YOUR-USERNAME/launchpad-m1
Direct Docs: https://github.com/YOUR-USERNAME/launchpad-m1/blob/main/docs/guides/00_INTEGRATION_QUICKSTART.md
Full Spec: https://github.com/YOUR-USERNAME/launchpad-m1/blob/main/docs/guides/15_m1_implementation_summary.md
```

---

## Troubleshooting

**Error: "Repository not found"**
- Verify repo was created on GitHub.com
- Check username is correct: `git remote -v` should show your repo

**Error: "Authentication failed"**
- Use GitHub CLI: `gh auth login`
- Or generate Personal Access Token: https://github.com/settings/tokens

**Error: "branch is ahead of origin/main by..."**
- You have local changes; commit them first: `git add -A && git commit -m "..."`

---

## Questions?

**Integration starts here:**
→ `docs/guides/00_INTEGRATION_QUICKSTART.md`

**Reference everything here:**
→ `docs/guides/MASTER_FILE_INDEX.txt`

**Understand the full design here:**
→ `docs/guides/15_m1_implementation_summary.md`

---

Ready to push? Follow Option A or B above. Takes 2 minutes. ✨
