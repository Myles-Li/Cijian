"""Generate a dependency-free Xcode project for the isolated feasibility app."""
from pathlib import Path
import json
from xml.sax.saxutils import escape

ROOT = Path(__file__).resolve().parent
objects = {}

def obj(kind, **values):
    key = f"{len(objects) + 1:024X}"
    objects[key] = {"isa": kind, **values}
    return key

def encode(value, depth=0):
    if isinstance(value, dict):
        return "{\n" + "\n".join("\t" * (depth + 1) + json.dumps(k) + " = " + encode(v, depth + 1) + ";" for k, v in value.items()) + "\n" + "\t" * depth + "}"
    if isinstance(value, list):
        return "(" + ", ".join(encode(v, depth) for v in value) + ",)" if value else "()"
    return json.dumps(str(value))

products = []
groups = []
targets = []
app_target = None
base = {"SDKROOT": "iphoneos", "IPHONEOS_DEPLOYMENT_TARGET": "17.0", "SWIFT_VERSION": "5.0",
        "CLANG_ENABLE_MODULES": "YES", "TARGETED_DEVICE_FAMILY": "1,2", "CODE_SIGN_STYLE": "Automatic",
        "SWIFT_OPTIMIZATION_LEVEL": "-Onone", "ENABLE_TESTABILITY": "YES", "DEBUG_INFORMATION_FORMAT": "dwarf",
        "SUPPORTED_PLATFORMS": "iphoneos iphonesimulator", "SWIFT_ACTIVE_COMPILATION_CONDITIONS": "DEBUG"}

def configurations(settings):
    configs = [obj("XCBuildConfiguration", name=name, buildSettings=settings.copy()) for name in ["Debug", "Release"]]
    return obj("XCConfigurationList", buildConfigurations=configs, defaultConfigurationIsVisible=0, defaultConfigurationName="Debug")

for name, source, kind in [("CijianProof", "Sources/ProofApp.swift", "application"),
                           ("CijianProofTests", "Tests/ProbeTests.swift", "bundle.unit-test"),
                           ("CijianProofUITests", "UITests/ProbeUITests.swift", "bundle.ui-testing")]:
    reference = obj("PBXFileReference", lastKnownFileType="sourcecode.swift", path=source, sourceTree="<group>")
    groups.append(reference)
    source_phase = obj("PBXSourcesBuildPhase", buildActionMask=2147483647,
                       files=[obj("PBXBuildFile", fileRef=reference)], runOnlyForDeploymentPostprocessing=0)
    resources = []
    if kind == "application":
        word_ref = obj("PBXFileReference", lastKnownFileType="text.json", path="Resources/words.json", sourceTree="<group>")
        groups.append(word_ref); resources.append(obj("PBXBuildFile", fileRef=word_ref))
    resources_phase = obj("PBXResourcesBuildPhase", buildActionMask=2147483647, files=resources, runOnlyForDeploymentPostprocessing=0)
    frameworks = obj("PBXFrameworksBuildPhase", buildActionMask=2147483647, files=[], runOnlyForDeploymentPostprocessing=0)
    product = obj("PBXFileReference", explicitFileType="wrapper.application" if kind == "application" else "wrapper.cfbundle",
                  includeInIndex=0, path=name + (".app" if kind == "application" else ".xctest"), sourceTree="BUILT_PRODUCTS_DIR")
    products.append(product)
    settings = {**base, "PRODUCT_NAME": name, "PRODUCT_BUNDLE_IDENTIFIER": "com.myles.cijian.iosproof" + ("" if kind == "application" else "." + name),
                "LD_RUNPATH_SEARCH_PATHS": "$(inherited) @executable_path/Frameworks @loader_path/Frameworks"}
    if kind == "application":
        settings["INFOPLIST_FILE"] = "Info.plist"
    else:
        settings["GENERATE_INFOPLIST_FILE"] = "YES"
        if kind == "bundle.unit-test":
            settings["TEST_HOST"] = "$(BUILT_PRODUCTS_DIR)/CijianProof.app/CijianProof"
            settings["BUNDLE_LOADER"] = "$(TEST_HOST)"
        else:
            settings["TEST_TARGET_NAME"] = "CijianProof"
    dependencies = []
    if app_target:
        dependencies = [obj("PBXTargetDependency", target=app_target)]
    target = obj("PBXNativeTarget", buildConfigurationList=configurations(settings), buildPhases=[source_phase, frameworks, resources_phase],
                 buildRules=[], dependencies=dependencies, name=name, productName=name, productReference=product,
                 productType="com.apple.product-type." + kind)
    targets.append(target)
    if app_target is None: app_target = target

product_group = obj("PBXGroup", children=products, name="Products", sourceTree="<group>")
main_group = obj("PBXGroup", children=groups + [product_group], sourceTree="<group>")
project = obj("PBXProject", attributes={"LastUpgradeCheck": "1600", "BuildIndependentTargetsInParallel": "YES"},
              buildConfigurationList=configurations({}), compatibilityVersion="Xcode 14.0", developmentRegion="zh_CN",
              knownRegions=["en", "zh_CN", "Base"], mainGroup=main_group, productRefGroup=product_group,
              projectDirPath="", projectRoot="", targets=targets)
result = {"archiveVersion": 1, "classes": {}, "objectVersion": 56, "objects": objects, "rootObject": project}
destination = ROOT / "CijianProof.xcodeproj"
destination.mkdir(exist_ok=True)
(destination / "project.pbxproj").write_text("// !$*UTF8*$!\n" + encode(result) + "\n", encoding="utf-8", newline="\n")
scheme_dir = destination / "xcshareddata/xcschemes"
scheme_dir.mkdir(parents=True, exist_ok=True)

def buildable(target):
    name = objects[target]["name"]
    product = name + (".app" if target == app_target else ".xctest")
    return f'<BuildableReference BuildableIdentifier="primary" BlueprintIdentifier="{target}" BuildableName="{product}" BlueprintName="{name}" ReferencedContainer="container:CijianProof.xcodeproj"/>'

scheme = f'''<?xml version="1.0" encoding="UTF-8"?>
<Scheme LastUpgradeVersion="1600" version="1.3">
<BuildAction parallelizeBuildables="YES" buildImplicitDependencies="YES"><BuildActionEntries>
<BuildActionEntry buildForTesting="YES" buildForRunning="YES" buildForProfiling="YES" buildForArchiving="YES" buildForAnalyzing="YES">{buildable(app_target)}</BuildActionEntry>
</BuildActionEntries></BuildAction>
<TestAction buildConfiguration="Debug" selectedDebuggerIdentifier="Xcode.DebuggerFoundation.Debugger.LLDB" selectedLauncherIdentifier="Xcode.IDEFoundation.Launcher.LLDB" shouldUseLaunchSchemeArgsEnv="YES"><Testables>
{''.join('<TestableReference skipped="NO">' + buildable(t) + '</TestableReference>' for t in targets[1:])}
</Testables></TestAction>
<LaunchAction buildConfiguration="Debug" selectedDebuggerIdentifier="Xcode.DebuggerFoundation.Debugger.LLDB" selectedLauncherIdentifier="Xcode.IDEFoundation.Launcher.LLDB" launchStyle="0" useCustomWorkingDirectory="NO" ignoresPersistentStateOnLaunch="NO" debugDocumentVersioning="YES" debugServiceExtension="internal" allowLocationSimulation="YES"><BuildableProductRunnable runnableDebuggingMode="0">{buildable(app_target)}</BuildableProductRunnable></LaunchAction>
<ProfileAction buildConfiguration="Release" shouldUseLaunchSchemeArgsEnv="YES" savedToolIdentifier="" useCustomWorkingDirectory="NO" debugDocumentVersioning="YES"><BuildableProductRunnable runnableDebuggingMode="0">{buildable(app_target)}</BuildableProductRunnable></ProfileAction>
<AnalyzeAction buildConfiguration="Debug"/><ArchiveAction buildConfiguration="Release" revealArchiveInOrganizer="YES"/>
</Scheme>'''
(scheme_dir / "CijianProof.xcscheme").write_text(scheme, encoding="utf-8", newline="\n")
print("Generated app, unit-test and UI-test targets; no developer credentials or external packages.")
