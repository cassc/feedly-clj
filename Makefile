build:
	lein uberjar
bump-release:
	lein release bump-release-version
bump-dev:
	lein release bump-dev-version
release: bump-release build bump-dev
