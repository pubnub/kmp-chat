echo "Build JS Chat SDK module artifacts"
pushd ..
./gradlew :jsNodeProductionLibraryDistribution
./gradlew :packJsPackage
mkdir -p js-chat/dist
cp build/packages/js/package.json js-chat/package.json
cp build/packages/js/index.d.ts js-chat/dist/
cp build/packages/js/index.d.ts js-chat/dist/index.es.d.ts
popd
npm run build
