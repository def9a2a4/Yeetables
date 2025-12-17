
.PHONY: build
build:
	cd yeetables && gradle build
	cp yeetables/build/libs/*.jar bin

.PHONY: clean
clean:
	cd yeetables && gradle clean


.PHONY: server-plugin-copy
server-plugin-copy:
	rm -f server/plugins/Yeetables*.jar
	rm -rf server/plugins/Yeetables/
	cp bin/*.jar server/plugins/

.PHONY: server-clear-plugin-data
	rm -rf server/plugins/yeetables/

.PHONY: server-start
server-start:
	cd server && java -Xmx2G -Xms2G -jar paper-1.21.10-105.jar nogui

.PHONY: server
server: server-plugin-copy server-start

.PHONY: all
all: build server