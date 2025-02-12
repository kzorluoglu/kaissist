# AI-Powered Git Commit Message Generator for PhpStorm

🚀 **Automate your Git commit messages with AI!**  
This PhpStorm plugin integrates with **Ollama's Llama 3** model to generate **intelligent commit messages** based on Git diffs. It ensures **clear, concise, and well-structured** commit messages while saving time on manual writing.

## ✨ Features
✅ **AI-Powered Commit Messages** – Generates structured commit messages based on changes.  
✅ **Supports Unversioned Files** – Includes newly created and modified files.  
✅ **Exact Formatting** – Enforces `ref #TICKET: Commit message` format.  
✅ **Automatic Git Diff Analysis** – Captures code changes for better message context.  
✅ **Works Locally** – No external API calls, only requires Ollama running on `localhost`.  

## ⚙️ Installation

### **Prerequisites**
- PhpStorm (2024.1 or later)
- Git installed and configured
- Ollama installed and running (`ollama serve`)

### **Steps**
1. Clone this repository:  
   ```bash
   git clone https://github.com/kzorluoglu/kaissist.git
   ```
2. Open PhpStorm and navigate to **Settings > Plugins**.
3. Click **"Install Plugin from Disk"** and select the downloaded plugin `.jar` file.
4. Restart PhpStorm.
5. Ensure Ollama is running:  
   ```bash
   ollama serve
   ```
6. Use the **"Generate Commit Message (Ollama)"** button in PhpStorm’s Git Commit window.

## 🛠 Usage

1. **Stage your changes** using `git add .`
2. Click **"Commit"** in PhpStorm.
3. Click **"Generate Commit Message (Ollama)"**.
4. Review the AI-generated message and commit your changes.

## 📌 Example

**Before:**  
```bash
git commit -m "Added new file"
```

**After (Generated by Plugin):**  
```bash
ref #12345: Added project initialization files and configuration settings.
```

## 🏗 Development

1. Clone the repo and open in **IntelliJ IDEA** or **PhpStorm**.
2. Build the project using Gradle/Maven.
3. Test the plugin by installing it manually in PhpStorm.

## 🤝 Contributing
We welcome contributions! Feel free to open **Issues** and **Pull Requests** to enhance this project.

## 📜 License
This project is licensed under the **MIT**.

## 📬 Contact
For questions or suggestions, feel free to open an issue or reach out to `hello@d8devs.com`.

---

**🚀 Simplify your Git workflow with AI-powered commit messages!**

