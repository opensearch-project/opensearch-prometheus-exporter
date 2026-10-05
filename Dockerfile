FROM gradle:8.12-jdk17

RUN useradd -m -u 1001 -s /bin/bash appuser && \
    apt-get update && \
    apt-get install -y sudo && \ 
    echo "appuser ALL=(ALL) NOPASSWD:ALL" > /etc/sudoers.d/appuser && \
    chmod 440 /etc/sudoers.d/appuser 

USER appuser

RUN mkdir -p /home/appuser/app

COPY . /home/appuser/app/

RUN cd /home/appuser/app && sudo chown -R appuser:appuser . && ./gradlew --info build