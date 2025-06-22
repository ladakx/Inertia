#!/usr/bin/env python3
import os
import argparse
import shutil

# --- КОНСТАНТИ (без змін) ---
BLACKLIST_DIRS = {
    '.git',
    '__pycache__',
    'node_modules',
    'venv',
    '.venv',
    'build',
    os.path.normpath('native/Jolt'),
    os.path.normpath('native/CMakeFiles'),
    'CMakeFiles',
    'Jolt',
    'jolt',
    'gpt_project'
}

BLACKLIST_FILES = {
    '.gitignore',
    '.env',
    'collect_gpt.py',
    'requirements.txt',
    'README.md',
    'collected_sources.md',
    'project.txt',
    'collect_structure.py',
    'package-lock.json',
    'yarn.lock',
    'settings.py',
    'local_settings.py',
    'secrets.json',
    'config.json',
    'project_structure.txt',
    'sc.py',
    'CMakeCache.txt'
}

WHITELIST_FILES = {
    # 'main.css',
}

EXTENSIONS = {'.kts', '.java', '.cpp', '.txt'}

COMMENT_STYLES = {
    '.cpp': ('/*', '*/'),
    '.java': ('/*', '*/'),
    '.kts': ('/*', '*/'),
    '.txt': ('###', '###'),
}

# --- ФУНКЦІЇ ЗБОРУ (без змін) ---
def is_whitelisted(filename, relative_path, whitelist_files):
    return (
        filename in whitelist_files or
        os.path.normpath(relative_path) in whitelist_files
    )

def collect_files(root_dir, extensions, blacklist_dirs, blacklist_files, whitelist_files=None):
    # Ця функція залишається без змін, бо вона правильно знаходить вихідні файли
    collected = []
    normalized_blacklist_dirs = {os.path.normpath(p) for p in blacklist_dirs}
    for dirpath, dirnames, filenames in os.walk(root_dir, topdown=True):
        dirs_to_remove = set()
        rel_dirpath = os.path.relpath(dirpath, root_dir)
        if rel_dirpath == '.': rel_dirpath = ''
        for d in dirnames:
            if d in normalized_blacklist_dirs or os.path.normpath(os.path.join(rel_dirpath, d)) in normalized_blacklist_dirs:
                dirs_to_remove.add(d)
        if dirs_to_remove:
            dirnames[:] = [d for d in dirnames if d not in dirs_to_remove]
        for filename in filenames:
            if filename in blacklist_files: continue
            rel_path = os.path.relpath(os.path.join(dirpath, filename), root_dir)
            if whitelist_files and not is_whitelisted(filename, rel_path, whitelist_files): continue
            ext = os.path.splitext(filename)[1].lower()
            if ext in extensions:
                collected.append(os.path.join(dirpath, filename))
    return collected

def generate_comment_header(file_path, root_dir):
    relative_path = os.path.relpath(file_path, root_dir)
    normalized_path = os.path.normpath(relative_path)
    ext = os.path.splitext(file_path)[1].lower()
    start_comment, end_comment = COMMENT_STYLES.get(ext, ('#', ''))
    comment_text = f" Original project path: {normalized_path} "
    return f"{start_comment}{comment_text}{end_comment}"

# --- ПОВНІСТЮ ПЕРЕПИСАНА ФУНКЦІЯ ОБРОБКИ І КОПІЮВАННЯ ---

def process_and_copy_files(file_list, output_dir, root_dir):
    """
    Готує папку, обробляє файли (додає коментар, змінює розширення)
    і копіює їх, вирішуючи конфлікти імен додаванням (n).
    """
    if os.path.exists(output_dir):
        shutil.rmtree(output_dir)
    os.makedirs(output_dir)

    # Сет для відстеження вже використаних імен файлів у папці призначення
    used_filenames = set()
    processed_count = 0
    duplicates_resolved = 0

    for path in file_list:
        # 1. Формуємо базове ім'я призначення
        # Наприклад, для 'src/com/main.java' -> 'main.java'
        original_basename = os.path.basename(path)

        # 2. Створюємо фінальне ім'я з новим форматом
        # 'main.java' -> 'main.java.txt'
        target_name = f"{original_basename}.txt"
        final_name = target_name

        # 3. Вирішуємо конфлікти імен
        counter = 1
        while final_name in used_filenames:
            if counter == 1: # Повідомляємо про перший знайдений дублікат
                duplicates_resolved += 1

            # Створюємо нове ім'я: 'main.java(1).txt', 'main.java(2).txt' і т.д.
            base, _ = os.path.splitext(target_name) # 'main.java'
            final_name = f"{base}({counter}).txt"
            counter += 1

        # Ми знайшли унікальне ім'я, резервуємо його
        used_filenames.add(final_name)

        destination_path = os.path.join(output_dir, final_name)

        # 4. Записуємо файл з заголовком
        try:
            header = generate_comment_header(path, root_dir)
            with open(path, 'r', encoding='utf-8', errors='ignore') as f_in:
                original_content = f_in.read()

            with open(destination_path, 'w', encoding='utf-8') as f_out:
                f_out.write(header)
                f_out.write("\n\n")
                f_out.write(original_content)

            processed_count += 1
        except Exception as e:
            print(f"Помилка обробки файлу {path}: {e}")

    return processed_count, duplicates_resolved

# --- ГОЛОВНА ФУНКЦІЯ ---

def main():
    parser = argparse.ArgumentParser(description='Збирає файли проєкту, перейменовуючи їх в формат `name.ext.txt` і вирішуючи конфлікти.')
    parser.add_argument('-w', '--whitelist', action='store_true', help='Використовувати whitelist файлів.')
    args = parser.parse_args()

    root_dir = os.getcwd()
    output_dir = os.path.join(root_dir, 'gpt_project')

    use_whitelist = args.whitelist and WHITELIST_FILES
    whitelist = WHITELIST_FILES if use_whitelist else None

    print("Починаю пошук файлів...")
    files_to_process = collect_files(root_dir, EXTENSIONS, BLACKLIST_DIRS, BLACKLIST_FILES, whitelist)

    if not files_to_process:
        print("Не знайдено жодного файлу за вашими критеріями.")
        return

    print(f"Знайдено {len(files_to_process)} файлів для обробки.")

    processed_count, duplicates_count = process_and_copy_files(files_to_process, output_dir, root_dir)

    print("-" * 20)
    print(f"Завершено. Оброблено та збережено {processed_count} файлів у папку '{os.path.basename(output_dir)}'.")
    if duplicates_count > 0:
        print(f"Вирішено {duplicates_count} конфлікт(ів) імен файлів шляхом додавання (n).")

if __name__ == "__main__":
    main()