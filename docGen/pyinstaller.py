import  os
if __name__ == '__main__':
    from PyInstaller.__main__ import run
    opts=['webApi2doc.py','-D']
    run(opts)
