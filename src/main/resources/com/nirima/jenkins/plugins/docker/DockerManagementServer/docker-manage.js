function stop(theId) {
    var input = document.getElementById('stopId');
    if (input != null) {
        input.value = theId;
    }

    var form = document.getElementById('control');
    form.submit();
}

document.addEventListener('DOMContentLoaded', () => {
    const stopContainerButtons = document.querySelectorAll('#control .stop-container');
    stopContainerButtons.forEach((button) =>
        button.addEventListener('click', () => {
            stop(button.dataset.id);
        }),
    );
});
