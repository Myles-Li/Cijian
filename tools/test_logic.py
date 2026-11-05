"""Portable Java 17 logic tests, without Android or external libraries."""
from pathlib import Path
import os,shutil,subprocess,tempfile
R=Path(__file__).resolve().parents[1]
java_home=Path(os.environ['JAVA_HOME']) if os.environ.get('JAVA_HOME') else None
suffix='.exe' if os.name=='nt' else ''
def binary(name):
 p=java_home/'bin'/(name+suffix) if java_home else None
 return str(p) if p and p.exists() else shutil.which(name)
javac,java=binary('javac'),binary('java')
if not javac or not java:raise SystemExit('Install JDK 17 or newer and set JAVA_HOME.')
names=['BackupReminder','StudyTasks','ReviewPlan','ReviewSession','FirstPass','StudyGroup','ShuffleDeck','SpeechPlan','Interval','Accent','TextSearch','WeakWords','PitchPattern','Pronunciation']
sources=[R/'app/src/main/java/com/myles/cijian'/(n+'.java') for n in names]+list((R/'tools/tests').glob('*.java'))
with tempfile.TemporaryDirectory(prefix='cijian-logic-') as out:
 subprocess.run([javac,'--release','17','-encoding','UTF-8','-d',out,*map(str,sources)],check=True)
 for cls in ['LogicTests','FeatureLogicTests','FinalLogicTests']:subprocess.run([java,'-cp',out,'com.myles.cijian.'+cls],check=True)
