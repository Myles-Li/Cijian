from pathlib import Path
import json
R=Path(__file__).resolve().parents[1];D=R/'docs'
data=json.loads((D/'结构图数据.json').read_text(encoding='utf-8-sig'))
# 正式清单仅嵌入为快照，不静默覆盖。 / Embed the master as a snapshot; never overwrite it.
master=R/'修改清单.md'
if not master.exists():master=R/'FEEDBACK.md'
data['master']=master.read_text(encoding='utf-8-sig') if master.exists() else ''
t=(D/'结构图模板.html').read_text(encoding='utf-8-sig')
assert t.count('__ATLAS_DATA__')==1
out=t.replace('__ATLAS_DATA__',json.dumps(data,ensure_ascii=False).replace('</','<\\/'))
(D/'词间-完整结构图.html').write_text(out,encoding='utf-8')
lines=['# 词间 '+data['baseline']+' 功能全量清单','','> 与交互图共用结构数据；候选代码能力与验收结果分开记录。','']
labels={'existing':'已有','proposal':'后续提案','inspect':'待审阅','missing':'未提供','boundary':'边界'}
for n in data['nodes']:
 lines+=['## '+n['title']+' · `'+n['id']+'`','','状态：'+labels[n['status']],'路径：'+n['path'],'',n['description'],'']
 if n['route']:lines+=['入口：'+n['route'],'']
 lines+=['- '+x for x in n['rules']]+['']
 if n['related']:lines+=['关联：'+'、'.join('`'+x+'`' for x in n['related']),'']
 lines+=['源码定位：'+'；'.join('`'+x['file']+':'+str(x['line'])+'`' for x in n['sources']),'']
(D/'功能全量清单.md').write_text('\n'.join(lines)+'\n',encoding='utf-8')
print('Generated offline atlas and text inventory:',len(data['nodes']),'nodes')
