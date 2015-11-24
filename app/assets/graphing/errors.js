function displayError(message) {
    console.log(message);
    var error = document.getElementById('error');
    error.innerHTML = message;
    error.style.display = 'block';
}