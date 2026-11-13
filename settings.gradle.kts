// 저장소 루트를 IDE(IntelliJ 등)에서 열 때 backend Gradle 프로젝트를 자동으로 인식하게 하는 composite build.
// 빌드·테스트 명령은 README 기준(cd backend && ./gradlew ...)을 그대로 쓴다.
// 루트 gradle/wrapper와 gradle-daemon-jvm.properties는 backend와 같은 버전으로 유지한다.
rootProject.name = "rag-quality-lab"

includeBuild("backend")
