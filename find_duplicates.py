import re
import os

# 读取strings.xml文件
file_path = os.path.join(os.path.dirname(__file__), "app", "src", "main", "res", "values", "strings.xml")

with open(file_path, "r", encoding="utf-8") as f:
    content = f.read()

# 使用正则表达式匹配所有字符串资源名称
pattern = r'<string name="([^"]*)"'
matches = re.findall(pattern, content)

# 统计每个资源名称出现的次数
resource_counts = {}
for match in matches:
    if match in resource_counts:
        resource_counts[match] += 1
    else:
        resource_counts[match] = 1

# 找出重复的资源名称
duplicates = {name: count for name, count in resource_counts.items() if count > 1}

# 输出重复的资源
if duplicates:
    print("发现以下重复的资源项:")
    for name, count in duplicates.items():
        print(f"  {name}: {count} 次")
        
    # 查找重复资源的位置
    print("\n重复资源的位置:")
    for name in duplicates:
        print(f"\n资源名称: {name}")
        pattern = f'<string name="{name}"'
        for i, line in enumerate(content.split('\n')):
            if re.search(pattern, line):
                print(f"  行 {i+1}: {line.strip()}")
else:
    print("没有发现重复的资源项")