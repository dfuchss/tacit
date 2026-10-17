## Download and install

Pick the file for your system:

| System | File |
|---|---|
| macOS (Apple Silicon) | `Tacit-{{version}}-macos-arm64.dmg` |
| Windows (x64) | `Tacit-{{version}}-windows-x64.zip` |
| Linux (x64) | `Tacit-{{version}}-linux-x64.zip` |

> **These builds are not signed** with an Apple or Microsoft code-signing certificate, so macOS and Windows
> warn you the first time you start Tacit. The steps below tell the system to go ahead. You only need to do this once.

### macOS

1. Open the `.dmg` and drag **Tacit** into your **Applications** folder.
2. Start Tacit. macOS blocks it, saying that Apple cannot check it for malicious software. Click **Done**.
3. Open **System Settings** > **Privacy & Security** and scroll down to the **Security** section.
4. Click **Open Anyway** next to the note about Tacit, then confirm with your login password.

The **Open Anyway** button only shows for about an hour after the failed attempt. If it is gone, start Tacit again.
The old right-click > **Open** shortcut no longer works on current macOS versions.

macOS asks again for every new version you download in a browser. Downloads made with `curl` are not flagged, so they
open without the prompt:

```bash
curl -LO https://github.com/dfuchss/tacit/releases/download/{{tag}}/Tacit-{{version}}-macos-arm64.dmg
open Tacit-{{version}}-macos-arm64.dmg
```

<details>
<summary>macOS says Tacit is "damaged and can't be opened"</summary>

The app is not corrupt. macOS marks downloaded apps it cannot verify with a quarantine flag. Clear it once:

```bash
xattr -cr /Applications/Tacit.app
```

</details>

### Windows

1. Unzip `Tacit-{{version}}-windows-x64.zip` and open the `Tacit` folder.
2. Run `Tacit.exe`.
3. If SmartScreen shows "Windows protected your PC", click **More info** and then **Run anyway**.

### Linux

1. Unzip `Tacit-{{version}}-linux-x64.zip`.
2. Run the launcher:

```bash
./Tacit/bin/Tacit
```

If it is not executable, run `chmod +x Tacit/bin/Tacit` first.

The web version is available as a Docker image: `ghcr.io/dfuchss/tacit`.
See the [README](https://gitlab.com/dfuchss/tacit#download--install) for details.
