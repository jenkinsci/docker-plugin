
function stop(theId) {
    var input = document.getElementById('stopId');
    if (input != null) {
        input.value = theId;
    }

    var form = document.getElementById('control');
    form.submit();
}
